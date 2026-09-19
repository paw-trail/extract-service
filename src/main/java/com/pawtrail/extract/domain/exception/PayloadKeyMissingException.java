package com.pawtrail.extract.domain.exception;

/**
 * 원문 블록은 있는데 규칙이 약속한 키가 없을 때 던집니다.
 *
 * 받은 쪽은 그 원문을 실패로 두고 다음 원문으로 넘어갑니다.
 * 빈 값으로 읽고 넘어가지 않는 이유는 조건 칸의 빈 값이 "정보 없음" 으로 판정에 섞이기 때문입니다.
 * 키 이름이 바뀐 것을 모른 채 수천 건이 "정보 없음" 으로 나가는 것보다
 * 실패 건수로 드러나는 편이 낫습니다.
 */
public class PayloadKeyMissingException extends RuntimeException {

    private final String block;
    private final String key;

    public PayloadKeyMissingException(String block, String key) {
        super(block + " 블록에 " + key + " 키가 없습니다");
        this.block = block;
        this.key = key;
    }

    public String block() {
        return block;
    }

    public String key() {
        return key;
    }
}
