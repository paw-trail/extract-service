package com.pawtrail.extract.infrastructure.provider.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ollama 네이티브 /api/chat 응답에서 쓰는 칸입니다.
 *
 * <pre>
 * message.content     모델이 쓴 답 (JSON 글)
 * done_reason         stop 이면 다 씀 · length 면 출력 길이 상한에 걸려 잘림
 * prompt_eval_count   입력 토큰 수 — 문맥 길이(num_ctx)를 채웠으면 입력 앞부분이 잘렸을 수 있음
 * eval_count          출력 토큰 수
 * </pre>
 *
 * 걸린 시간 · 모델 정보 같은 나머지 칸은 읽지 않습니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaChatResponse(
        Message message,
        @JsonProperty("done_reason") String doneReason,
        @JsonProperty("prompt_eval_count") Integer promptEvalCount,
        @JsonProperty("eval_count") Integer evalCount
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            String role,
            String content
    ) {
    }
}
