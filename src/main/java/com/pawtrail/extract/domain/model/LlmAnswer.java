package com.pawtrail.extract.domain.model;

import java.util.List;

/**
 * 모델이 준 답 그대로입니다.
 *
 * 조건 칸은 모델이 채운 값이고 근거는 아직 조각 번호입니다.
 * 근거 검사(CitationCheck)를 거쳐야 조건과 근거로 쓸 수 있습니다.
 */
public record LlmAnswer(
        ConditionFields fields,
        List<LlmCitation> citations
) {

    public LlmAnswer {
        fields = fields == null ? ConditionFields.empty() : fields;
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
