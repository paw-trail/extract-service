package com.pawtrail.extract.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 실행을 다른 스레드에서 돌립니다.
 *
 * <b>트리거와 클래스를 나눈 이유는 스프링 프록시입니다.</b>
 * 같은 객체 안에서 부르면 @Async 가 걸리지 않아 트리거 응답이 실행이 끝날 때까지(전량이면 1시간)
 * 막힙니다. ingest 의 IngestTriggerService 와 IngestExecutor 를 나눈 것과 같은 까닭입니다.
 *
 * <b>끝나면 반드시 onFinish 를 부릅니다.</b>
 * 트리거가 쥔 "실행 중" 표시를 여기서 풀어야 다음 트리거가 409 에 막히지 않습니다.
 * 예외로 끝나도 풀리도록 finally 에 둡니다.
 */
@Service
public class ExtractExecutor {

    private static final Logger log = LoggerFactory.getLogger(ExtractExecutor.class);

    private final ExtractRunner runner;

    public ExtractExecutor(ExtractRunner runner) {
        this.runner = runner;
    }

    @Async
    public void execute(LocalDateTime startedAt, Integer limit, Runnable onFinish) {
        try {
            runner.run(startedAt, limit);
        } catch (RuntimeException e) {
            // 실행이 잡지 못한 예외 — 요약 로그가 안 남으므로 여기서 남김
            log.error("추출 실행이 예상하지 못한 예외로 끝났습니다. 시작={}", startedAt, e);
        } finally {
            onFinish.run();
        }
    }
}
