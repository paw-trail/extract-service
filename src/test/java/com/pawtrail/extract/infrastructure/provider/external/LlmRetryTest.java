package com.pawtrail.extract.infrastructure.provider.external;

import com.pawtrail.extract.domain.exception.LlmUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmRetryTest {

    private final List<Long> waits = new ArrayList<>();
    private final LlmRetry retry = new LlmRetry(3, 1000, waits::add);

    @Test
    @DisplayName("일시적 실패 뒤 성공하면 그 답을 쓴다")
    void 한_번_뒤_성공() {
        AtomicInteger attempts = new AtomicInteger();

        String result = retry.call(() -> {
            if (attempts.incrementAndGet() == 1) {
                throw new HttpServerErrorException(HttpStatus.BAD_GATEWAY);
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(waits).containsExactly(1000L);
    }

    @Test
    @DisplayName("일시적 실패가 이어지면 1초부터 두 배로 세 번 기다린 뒤 멈춘다")
    void 계속_일시적() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retry.call(() -> {
            attempts.incrementAndGet();
            throw new ResourceAccessException("Read timed out");
        })).isInstanceOf(LlmUnavailableException.class);

        assertThat(attempts).hasValue(4);
        assertThat(waits).containsExactly(1000L, 2000L, 4000L);
    }

    @Test
    @DisplayName("인증 실패는 재시도해도 나아지지 않아 바로 멈춘다")
    void 설정_문제() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retry.call(() -> {
            attempts.incrementAndGet();
            throw new HttpClientErrorException(HttpStatus.UNAUTHORIZED);
        })).isInstanceOf(LlmUnavailableException.class);

        assertThat(attempts).hasValue(1);
        assertThat(waits).isEmpty();
    }

    @Test
    @DisplayName("일시적인 것 — 연결 실패 · 제한시간 · 408 · 429 · 5xx")
    void 일시적_가르기() {
        assertThat(LlmRetry.isTransient(new ResourceAccessException("Connection refused"))).isTrue();
        assertThat(LlmRetry.isTransient(new HttpClientErrorException(HttpStatus.REQUEST_TIMEOUT))).isTrue();
        assertThat(LlmRetry.isTransient(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS))).isTrue();
        assertThat(LlmRetry.isTransient(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE))).isTrue();

        assertThat(LlmRetry.isTransient(new HttpClientErrorException(HttpStatus.BAD_REQUEST))).isFalse();
        assertThat(LlmRetry.isTransient(new HttpClientErrorException(HttpStatus.NOT_FOUND))).isFalse();
        assertThat(LlmRetry.isTransient(new RestClientException("응답을 읽지 못했습니다"))).isFalse();
    }
}
