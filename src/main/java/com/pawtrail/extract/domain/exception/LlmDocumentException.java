package com.pawtrail.extract.domain.exception;

/**
 * 이 문서에 대한 모델의 답을 쓸 수 없을 때 던집니다.
 *
 * 받은 쪽은 재시도 없이 그 문서만 실패로 둡니다. 같은 입력은 같은 답을 받으므로
 * 다시 불러도 결과가 같습니다. 프롬프트를 고친 뒤 실패 문서를 대기로 되돌려 다시 돌립니다.
 */
public class LlmDocumentException extends RuntimeException {

    /**
     * <pre>
     * TRUNCATED        출력 길이 상한에 걸려 답이 잘림
     * INPUT_TOO_LONG   입력이 문맥 길이를 넘어 앞부분이 잘렸을 수 있음
     * INVALID_ANSWER   답이 JSON 이 아니거나 스키마와 다름
     * REFUSED          모델이 답하기를 거절함
     * </pre>
     */
    public enum Reason {
        TRUNCATED,
        INPUT_TOO_LONG,
        INVALID_ANSWER,
        REFUSED
    }

    private final Reason reason;

    public LlmDocumentException(Reason reason, String message) {
        this(reason, message, null);
    }

    public LlmDocumentException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
