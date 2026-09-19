package com.pawtrail.extract.infrastructure.provider.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * OpenAI /v1/chat/completions 요청입니다.
 *
 * <pre>
 * reasoning_effort   추론에 쓰는 힘 — 설정 값(medium · 정확도 평가로 정함)
 * response_format    json_schema · strict — 응답 스키마(LlmPrompt.schema)를 그대로 실음
 * </pre>
 *
 * 온도는 보내지 않습니다. 추론 모델은 온도를 받지 않습니다.
 * 그래서 같은 입력에도 실행마다 답이 조금씩 다를 수 있습니다 (평가에서 같은 설정도 40건에 5~10칸).
 */
public record OpenAiChatRequest(
        String model,
        @JsonProperty("reasoning_effort") String reasoningEffort,
        List<Message> messages,
        @JsonProperty("response_format") Map<String, Object> responseFormat
) {

    public record Message(
            String role,
            String content
    ) {
    }
}
