package com.pawtrail.extract.domain.model;

import java.util.List;

/**
 * 근거 검사를 거친 LLM 결과입니다.
 *
 * <pre>
 * fields            근거가 있는 값만 남은 조건 칸
 * evidence          남은 값마다 원문 조각으로 만든 근거
 * droppedValues     값은 있는데 근거가 없어 버린 칸 수
 * ignoredCitations  비어 있는 칸이나 조건 칸이 아닌 이름을 가리켜 무시한 근거 수
 * droppedNumbers    조각 범위를 벗어나 버린 번호 수
 * </pre>
 *
 * 세 수는 로그와 정확도 평가가 봅니다. 모델이 근거를 얼마나 빠뜨리는지 드러나게 하려는 것입니다.
 */
public record LlmReading(
        ConditionFields fields,
        List<Evidence> evidence,
        int droppedValues,
        int ignoredCitations,
        int droppedNumbers
) {

    public LlmReading {
        evidence = List.copyOf(evidence);
    }

    public static LlmReading empty() {
        return new LlmReading(ConditionFields.empty(), List.of(), 0, 0, 0);
    }
}
