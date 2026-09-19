package com.pawtrail.extract.infrastructure.provider.external.dto;

import com.pawtrail.extract.domain.model.ConditionFields;

import java.util.List;

/**
 * 모델이 준 답의 JSON 모양입니다 — 응답 스키마(LlmPrompt.schema)와 같습니다.
 *
 * 두 구현이 받은 글을 이 모양으로 읽은 뒤 도메인의 LlmAnswer 로 옮깁니다.
 * 조건 칸은 ConditionFields 에 바로 읽습니다. 칸 이름과 타입이 스키마와 같게 맞춰져 있습니다.
 *
 * 스키마에 없는 속성은 받지 않습니다. 거부는 LlmAnswerParser 가 읽을 때 켭니다.
 */
public record LlmAnswerJson(
        ConditionFields fields,
        List<Citation> evidence
) {

    public record Citation(
            String fieldName,
            List<Integer> segments
    ) {
    }
}
