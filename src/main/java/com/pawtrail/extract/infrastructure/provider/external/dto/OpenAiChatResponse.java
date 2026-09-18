package com.pawtrail.extract.infrastructure.provider.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * OpenAI /v1/chat/completions 응답에서 쓰는 칸입니다.
 *
 * <pre>
 * choices[0].message.content   모델이 쓴 답 (JSON 글)
 * choices[0].message.refusal   모델이 답하기를 거절하면 그 사유가 옴
 * choices[0].finish_reason     stop 이면 다 씀 · length 면 출력 길이 상한에 걸려 잘림
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiChatResponse(
        List<Choice> choices
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            Message message,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            String content,
            String refusal
    ) {
    }
}
