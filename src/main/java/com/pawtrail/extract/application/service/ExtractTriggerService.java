package com.pawtrail.extract.application.service;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.extract.application.dto.output.TriggerOutput;
import com.pawtrail.extract.domain.exception.ExtractErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 실행 요청을 받아 비동기로 넘깁니다. 이미 돌고 있으면 409 로 거절합니다.
 *
 * <b>"실행 중" 은 메모리의 표시 하나로 봅니다.</b>
 * 실행 기록 표가 없어 DB 로 막을 수 없고, extract 는 한 대만 띄웁니다.
 * 트리거는 Jenkins 잡과 사람이라 동시에 두 번 들어올 일이 드물지만,
 * 들어오면 두 실행이 같은 대기열 첫 쪽을 함께 가져가 같은 원문을 두 번 뽑습니다.
 * 앱이 재기동되면 표시가 풀리는데, 그때는 돌던 실행도 함께 끝났으므로 맞습니다.
 */
@Service
public class ExtractTriggerService {

    private static final Logger log = LoggerFactory.getLogger(ExtractTriggerService.class);

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExtractExecutor executor;

    public ExtractTriggerService(ExtractExecutor executor) {
        this.executor = executor;
    }

    /**
     * @param limit 처리할 원문 수의 상한 — null 이면 대기가 빌 때까지
     * @throws CustomException EXTRACT_ALREADY_RUNNING — 이미 돌고 있을 때
     */
    public TriggerOutput start(Integer limit) {
        if (!running.compareAndSet(false, true)) {
            throw new CustomException(ExtractErrorCode.EXTRACT_ALREADY_RUNNING);
        }
        LocalDateTime startedAt = LocalDateTime.now();
        try {
            executor.execute(startedAt, limit, () -> running.set(false));
        } catch (RuntimeException e) {
            // 실행기가 일을 받지 못함 (스레드 풀이 가득 찬 식) — 표시를 풀어야 다음 트리거가 막히지 않음
            running.set(false);
            throw e;
        }
        log.info("추출 실행을 받았습니다. 시작={} 상한={}", startedAt, limit == null ? "없음" : limit);
        return new TriggerOutput(startedAt, limit);
    }

    /**
     * 지금 실행이 돌고 있는지입니다. 테스트와 로그가 봅니다.
     */
    public boolean isRunning() {
        return running.get();
    }
}
