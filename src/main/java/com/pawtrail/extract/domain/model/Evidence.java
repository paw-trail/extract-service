package com.pawtrail.extract.domain.model;

import com.pawtrail.extract.domain.enums.ExtractionMethod;

/**
 * 칸 하나의 근거입니다. policy bulk 요청의 근거 한 줄과 모양이 같습니다.
 *
 * <pre>
 * fieldName         근거가 받치는 칸 — FieldNames 의 이름
 * originField       원문에서 그 값이 나온 키
 * segmentIndex      그 키의 값을 조각으로 나눴을 때 몇 번째인지 — 규칙 근거는 비움
 * segmentText       근거 문구 — 원문 그대로
 * extractionMethod  이 근거를 읽은 쪽 — 규칙이면 RULE · 모델이면 LLM
 * </pre>
 *
 * <b>근거 문구는 늘 원문에서 옵니다.</b>
 * 규칙 근거는 원문 값을 그대로 쓰고, LLM 근거도 모델이 쓴 문장이 아니라
 * 우리가 나눈 조각을 번호로 가리키게 합니다. 모델이 만든 문장을 근거로 두면
 * 모델이 고른 값을 모델의 말로 확인하는 순환이 됩니다.
 *
 * <b>읽은 쪽은 근거를 만드는 자리에서 정합니다.</b>
 * 판정 화면이 이유마다 "공공데이터 항목" 과 "안내문을 AI 가 읽음" 을 가르는 재료이고,
 * policy 가 근거 줄마다 받아 저장합니다 (policy v0.1.2 부터 필수).
 * 원문 키나 조각 번호로는 가를 수 없습니다. 규칙과 모델이 함께 읽는 키가 있고,
 * 모델 근거도 통째로 읽는 칸이면 조각 번호가 비기 때문입니다.
 * 출처 행의 추출 방식은 행 단위라 칸마다 갈리면 MIXED 가 되므로 근거 한 줄에는 쓰지 않습니다.
 */
public record Evidence(
        String fieldName,
        String originField,
        Integer segmentIndex,
        String segmentText,
        ExtractionMethod extractionMethod
) {

    /**
     * 규칙이 읽은 칸의 근거입니다. 조각으로 나누지 않으므로 조각 번호가 없습니다.
     */
    public static Evidence ofRule(String fieldName, String originField, String segmentText) {
        return new Evidence(fieldName, originField, null, segmentText, ExtractionMethod.RULE);
    }

    /**
     * 모델이 읽은 칸의 근거입니다. 우리가 나눈 조각을 가리키며, 통째로 읽는 칸이면 조각 번호가 비어 있습니다.
     */
    public static Evidence ofLlm(String fieldName, String originField, Integer segmentIndex, String segmentText) {
        return new Evidence(fieldName, originField, segmentIndex, segmentText, ExtractionMethod.LLM);
    }
}
