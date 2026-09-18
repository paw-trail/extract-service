package com.pawtrail.extract.domain.exception;

/**
 * 모델을 부를 수 없어 이번 실행을 더 할 수 없을 때 던집니다.
 *
 * <pre>
 * 일시적     연결 실패 · 제한시간 · 과부하 · 5xx — 세 번 재시도한 뒤에도 안 될 때
 * 설정 문제   인증 · 권한 · 없는 모델 — 재시도해도 나아지지 않아 바로
 * </pre>
 *
 * 받은 쪽은 실행을 멈추고 원문 상태를 바꾸지 않습니다. 원문이 대기로 남아 있어
 * 모델이 돌아온 뒤 같은 트리거로 이어 갑니다. 문서마다 실패로 두면 모델이 꺼진 동안
 * 대기열이 통째로 실패가 됩니다.
 */
public class LlmUnavailableException extends RuntimeException {

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
