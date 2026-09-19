package com.pawtrail.extract.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 조건을 읽는 언어 모델 설정입니다 (app.extract.llm).
 *
 * provider 가 ollama 면 로컬 Ollama 를, openai 면 OpenAI 를 씁니다. 뜨는 구현은 하나입니다.
 * 전량 적재와 배포의 증분 모두 OpenAI(gpt-5.6-luna · 추론 medium)를 씁니다.
 * 정확도 평가에서 새 문서 40건 기준 로컬 qwen3.8:27b 보다 정밀도 · 재현율이 모두 높았고
 * (95.6% · 93.2% 대 93.6% · 88.0%), 전량도 어림으로 몇 달러였습니다.
 * 로컬 Ollama 는 설정 한 줄로 되돌릴 수 있게 남겨 둡니다. 코드는 같고 이 값만 다릅니다.
 *
 * <b>재시도 값도 여기 둡니다.</b>
 * 재시도는 모델 호출에만 합니다. ingest 와 policy 호출은 실패하면 실행을 바로 멈추므로
 * 재시도 값이 쓰일 곳이 모델 호출뿐입니다.
 *
 * <b>OpenAI 키는 provider 가 openai 일 때만 검사합니다.</b>
 * 키는 config 저장소에 두지 않고 OPENAI_API_KEY 환경변수에서 옵니다.
 * 테스트와 로컬 Ollama 로 되돌린 실행은 키 없이 떠야 하므로 늘 요구하지 않습니다.
 *
 * 검증을 더할 때는 세 곳을 함께 봅니다.
 * <pre>
 * config 저장소의 extract-service.yml
 * src/test/resources/application.yml   설정 서버를 끄므로 값이 여기서만 옴
 * 이 클래스
 * </pre>
 *
 * @param provider       쓸 구현 — ollama 또는 openai
 * @param maxRetries     일시적 실패를 다시 부르는 횟수
 * @param retryBackoffMs 첫 재시도까지 기다리는 시간 — 다시 부를 때마다 두 배
 * @param ollama         로컬 Ollama 값
 * @param openai         OpenAI 값
 */
@Validated
@ConfigurationProperties(prefix = "app.extract.llm")
public record LlmProperties(

        @NotNull(message = "app.extract.llm.provider 가 필요합니다 (ollama 또는 openai)")
        Provider provider,

        @Positive(message = "app.extract.llm.max-retries 는 양수여야 합니다")
        int maxRetries,

        @Positive(message = "app.extract.llm.retry-backoff-ms 는 양수여야 합니다")
        long retryBackoffMs,

        @Valid
        @NotNull(message = "app.extract.llm.ollama 가 필요합니다")
        Ollama ollama,

        @Valid
        @NotNull(message = "app.extract.llm.openai 가 필요합니다")
        OpenAi openai
) {

    public enum Provider {
        OLLAMA,
        OPENAI
    }

    /**
     * @param baseUrl        Ollama 주소 — 로컬은 localhost:11434 · 컨테이너는 OLLAMA_BASE_URL 로 덮음
     * @param model          모델 이름 — 추출 기록(extractedBy)에도 남음
     * @param think          생각 모드 — 끔 (정확도 평가에서 켜면 재현율은 88.0 → 91.5% 로 오르나
     *                       정밀도가 93.6 → 91.5% 로 내려가고 7배 느렸음)
     * @param numCtx         문맥 길이 — 가장 긴 입력(3천 자 남짓)과 프롬프트를 넉넉히 담는 값
     * @param numPredict     출력 길이 상한 — 넘으면 답이 잘려 그 문서만 실패로 둠
     * @param temperature    0 — 같은 입력에 같은 답 (다시 보내도 판이 안 오르게)
     * @param seed           고정 — 온도 0 과 함께 결과를 되풀이할 수 있게
     * @param timeoutSeconds 호출 제한 시간 — 첫 호출은 모델을 올리느라 80초 가까이 걸린 적이 있음
     */
    public record Ollama(

            @NotBlank(message = "app.extract.llm.ollama.base-url 이 필요합니다")
            String baseUrl,

            @NotBlank(message = "app.extract.llm.ollama.model 이 필요합니다")
            String model,

            boolean think,

            @Positive(message = "app.extract.llm.ollama.num-ctx 는 양수여야 합니다")
            int numCtx,

            @Positive(message = "app.extract.llm.ollama.num-predict 는 양수여야 합니다")
            int numPredict,

            @PositiveOrZero(message = "app.extract.llm.ollama.temperature 는 0 이상이어야 합니다")
            double temperature,

            int seed,

            @Positive(message = "app.extract.llm.ollama.timeout-seconds 는 양수여야 합니다")
            long timeoutSeconds
    ) {
    }

    /**
     * @param baseUrl         OpenAI 주소
     * @param model           모델 이름 — 추출 기록(extractedBy)에도 남음
     * @param reasoningEffort 추론에 쓰는 힘 — medium (정확도 평가에서 low 보다 재현율이 크게 높았고
     *                        high 와는 차이를 가려낼 수 없었음)
     * @param secondReasoningEffort 두 번째 읽기의 추론 강도 — high · 비우면 한 번만 읽음
     *                        두 읽기를 안전 쪽으로 합치면 허용을 넓히는 틀림이 절반으로 줄었음
     *                        (정확도 평가 새 표본 40건 · 정규화 뒤 4칸 → 2칸)
     * @param apiKey          OPENAI_API_KEY 환경변수 — provider 가 openai 일 때만 검사
     * @param timeoutSeconds  호출 제한 시간
     */
    public record OpenAi(

            @NotBlank(message = "app.extract.llm.openai.base-url 이 필요합니다")
            String baseUrl,

            @NotBlank(message = "app.extract.llm.openai.model 이 필요합니다")
            String model,

            @NotBlank(message = "app.extract.llm.openai.reasoning-effort 가 필요합니다")
            String reasoningEffort,

            String secondReasoningEffort,

            String apiKey,

            @Positive(message = "app.extract.llm.openai.timeout-seconds 는 양수여야 합니다")
            long timeoutSeconds
    ) {
    }

    @AssertTrue(message = "app.extract.llm.provider 가 openai 면 OPENAI_API_KEY 환경변수가 필요합니다")
    public boolean isOpenAiKeyPresent() {
        return provider != Provider.OPENAI
                || (openai != null && openai.apiKey() != null && !openai.apiKey().isBlank());
    }
}
