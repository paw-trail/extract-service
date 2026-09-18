package com.pawtrail.extract.domain.exception;

/**
 * 우리 서비스(ingest · policy)를 부르지 못했습니다. 실행을 멈춥니다.
 *
 * 모델 호출과 달리 재시도하지 않습니다. 상대가 한 서비스라 한 번 실패하면
 * 다음 청크도 같은 이유로 실패하고, 이어 가면 같은 오류만 쌓입니다.
 * 멈출 때 그 청크의 원문 상태를 바꾸지 않으므로 다음 실행이 같은 원문부터 다시 가져갑니다.
 *
 * 응답을 받았으나 약속과 다를 때(보낸 수와 받은 수가 다름 같은)도 이 예외입니다.
 */
public class InternalCallException extends RuntimeException {

    public InternalCallException(String message) {
        super(message);
    }

    public InternalCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
