package com.pawtrail.extract.infrastructure.provider.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * OpenAI /v1/chat/completions 요청입니다.
 *
 * <pre>
 * reasoning_effort   추론에 쓰는 힘 — 문장에서 칸을 고르는 일이라 low 로 충분함
 * response_format    json_schema · strict — 응답 스키마(LlmPrompt.schema)를 그대로 실음
 * </pre>
 *
 * 온도는 보내지 않습니다. 추론 모델은 온도를 받지 않습니다.
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
