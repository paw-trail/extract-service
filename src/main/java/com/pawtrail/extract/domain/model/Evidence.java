package com.pawtrail.extract.domain.model;

/**
 * 칸 하나의 근거입니다. policy bulk 요청의 근거 한 줄과 모양이 같습니다.
 *
 * <pre>
 * fieldName     근거가 받치는 칸 — FieldNames 의 이름
 * originField   원문에서 그 값이 나온 키
 * segmentIndex  그 키의 값을 조각으로 나눴을 때 몇 번째인지 — 규칙 근거는 비움
 * segmentText   근거 문구 — 원문 그대로
 * </pre>
 *
 * <b>근거 문구는 늘 원문에서 옵니다.</b>
 * 규칙 근거는 원문 값을 그대로 쓰고, LLM 근거도 모델이 쓴 문장이 아니라
 * 우리가 나눈 조각을 번호로 가리키게 합니다. 모델이 만든 문장을 근거로 두면
 * 모델이 고른 값을 모델의 말로 확인하는 순환이 됩니다.
 */
public record Evidence(
        String fieldName,
        String originField,
        Integer segmentIndex,
        String segmentText
) {

    /**
     * 규칙이 읽은 칸의 근거입니다. 조각으로 나누지 않으므로 조각 번호가 없습니다.
     */
    public static Evidence ofRule(String fieldName, String originField, String segmentText) {
        return new Evidence(fieldName, originField, null, segmentText);
    }
}
