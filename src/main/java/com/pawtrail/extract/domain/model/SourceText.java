package com.pawtrail.extract.domain.model;

/**
 * LLM 에 넘길 원문 칸 하나입니다.
 *
 * 규칙이 못 읽는 문장을 칸 이름과 함께 그대로 담습니다.
 * 조각으로 나누고 번호를 붙이는 일은 LLM 추출이 맡습니다.
 *
 * <pre>
 * originField   원문의 키 — 근거의 originField 가 됨
 * text          그 키의 값 — 앞뒤 공백만 걷고 줄바꿈은 그대로 둠
 * </pre>
 */
public record SourceText(
        String originField,
        String text
) {
}
