package com.pawtrail.extract.infrastructure.provider.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Ollama 네이티브 /api/chat 요청입니다.
 *
 * OpenAI 호환 주소(/v1)가 아니라 네이티브를 쓰는 이유는 options 입니다.
 * 호환 주소는 요청의 num_ctx 를 무시하고 서버 환경변수만 따라, 두지 않으면 문맥이 조용히 줄어듭니다.
 *
 * <pre>
 * stream    false — 답을 한 번에 받음
 * think     false — 생각 모드를 끔 (시험에서 끈 채로 정확도가 맞았고 생각은 토큰 · 시간이 몇 배)
 * format    응답 스키마 — 모델 출력을 이 모양으로 묶음
 * options   온도 0 · seed 고정 · 문맥 · 출력 길이
 * </pre>
 */
public record OllamaChatRequest(
        String model,
        boolean stream,
        boolean think,
        Map<String, Object> format,
        List<Message> messages,
        Options options
) {

    public record Message(
            String role,
            String content
    ) {
    }

    public record Options(
            double temperature,
            int seed,
            @JsonProperty("num_ctx") int numCtx,
            @JsonProperty("num_predict") int numPredict
    ) {
    }
}
