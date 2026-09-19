package com.pawtrail.extract.domain.model;

/**
 * 모델에 보내는 원문 조각 하나입니다.
 *
 * <pre>
 * number        문서 전체에서 1부터 이어 붙인 번호 — 모델은 근거를 이 번호로 답함
 * originField   원문의 키 — 근거의 originField 가 됨
 * indexInField  그 키 안에서 몇 번째 조각인지 1부터 — 나누지 않는 칸은 비움 (근거의 segmentIndex)
 * text          조각 글 — 근거 문구가 됨
 * </pre>
 *
 * 근거 문구는 늘 이 조각에서 옵니다. 모델은 번호만 고르고 문장을 쓰지 않으므로
 * 화면에 뜨는 근거가 원문에 없는 말일 수 없습니다.
 */
public record Segment(
        int number,
        String originField,
        Integer indexInField,
        String text
) {
}
