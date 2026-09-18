package com.pawtrail.extract.infrastructure.provider.external;

import com.pawtrail.extract.domain.exception.LlmUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.LongConsumer;
import java.util.function.Supplier;

/**
 * 모델 호출을 감싸 실패를 가르고, 일시적인 것만 다시 부릅니다. 두 구현이 함께 씁니다.
 *
 * <pre>
 * 일시적     연결 실패 · 제한시간 · 408 · 429 · 5xx   1초부터 두 배로 기다리며 max-retries 번 다시 부름
 *                                                   그래도 안 되면 LlmUnavailableException — 실행을 멈춤
 * 설정 문제   그 밖의 4xx (인증 · 권한 · 없는 모델)      재시도 없이 바로 LlmUnavailableException
 *           응답을 읽지 못함                          재시도 없이 바로 LlmUnavailableException
 * </pre>
 *
 * 설정 문제를 다시 부르지 않는 이유는 ingest 의 callWithRetry 와 같습니다 — 고쳐야 나아지는 실패는
 * 기다려도 그대로입니다. 어느 쪽이든 실행을 멈추는 것은 같고 재시도만 하지 않습니다.
 *
 * 모델 답이 잘리거나 형식이 틀린 것(문서 탓)은 여기 오지 않습니다 — 호출은 성공했기 때문이며
 * 구현이 응답을 본 뒤 LlmDocumentException 으로 가릅니다.
 */
@Slf4j
public class LlmRetry {

    private final int maxRetries;
    private final long backoffMs;
    private final LongConsumer sleeper;

    public LlmRetry(int maxRetries, long backoffMs) {
        this(maxRetries, backoffMs, LlmRetry::sleep);
    }

    // 테스트는 기다리지 않도록 sleeper 를 바꿔 넣음
    LlmRetry(int maxRetries, long backoffMs, LongConsumer sleeper) {
        this.maxRetries = maxRetries;
        this.backoffMs = backoffMs;
        this.sleeper = sleeper;
    }

    public <T> T call(Supplier<T> call) {
        long wait = backoffMs;
        for (int attempt = 1; ; attempt++) {
            try {
                return call.get();
            } catch (RestClientException e) {
                if (!isTransient(e)) {
                    throw new LlmUnavailableException("재시도해도 나아지지 않는 모델 호출 실패입니다: " + e.getMessage(), e);
                }
                if (attempt > maxRetries) {
                    throw new LlmUnavailableException(
                            "모델 호출이 " + maxRetries + "번 재시도한 뒤에도 실패했습니다: " + e.getMessage(), e);
                }
                log.warn("모델 호출이 일시적으로 실패해 {}ms 뒤 다시 부릅니다 ({}/{}): {}",
                        wait, attempt, maxRetries, e.getMessage());
                sleeper.accept(wait);
                wait *= 2;
            }
        }
    }

    static boolean isTransient(RestClientException e) {
        if (e instanceof ResourceAccessException) {
            return true;
        }
        if (e instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            return status == 408 || status == 429 || status >= 500;
        }
        return false;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmUnavailableException("재시도를 기다리다 멈췄습니다", e);
        }
    }
}
