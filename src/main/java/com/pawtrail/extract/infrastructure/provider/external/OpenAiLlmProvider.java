package com.pawtrail.extract.infrastructure.provider.external;

import com.pawtrail.extract.domain.exception.LlmDocumentException;
import com.pawtrail.extract.domain.model.LlmAnswer;
import com.pawtrail.extract.domain.model.Segment;
import com.pawtrail.extract.domain.provider.LlmProvider;
import com.pawtrail.extract.infrastructure.config.LlmProperties;
import com.pawtrail.extract.infrastructure.provider.external.dto.OpenAiChatRequest;
import com.pawtrail.extract.infrastructure.provider.external.dto.OpenAiChatResponse;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI(/v1/chat/completions)로 조건을 읽습니다.
 *
 * 배포에서 증분을 돌릴 때 설정 키 하나(app.extract.llm.provider: openai)로 바꿔 씁니다.
 * 프롬프트 · 응답 스키마 · 입력 글은 Ollama 구현과 같은 것을 씁니다.
 * 빈을 LlmConfig 가 만드는 이유는 OllamaLlmProvider 와 같습니다.
 *
 * <b>응답을 보고 그 문서만 실패로 두는 경우</b>
 * <pre>
 * finish_reason 이 length            출력 길이 상한에 걸려 답이 잘림
 * message.refusal 이 있음             모델이 답하기를 거절함
 * 답이 JSON 이 아니거나 모양이 다름     LlmAnswerParser 가 가름
 * </pre>
 */
public class OpenAiLlmProvider implements LlmProvider {

    // 응답 스키마의 이름 — OpenAI 가 요구하는 식별자일 뿐 다른 뜻은 없음
    static final String SCHEMA_NAME = "pet_conditions";

    private final RestClient restClient;
    private final LlmProperties.OpenAi settings;
    private final JsonMapper jsonMapper;
    private final LlmRetry retry;

    public OpenAiLlmProvider(RestClient restClient, LlmProperties.OpenAi settings,
                             JsonMapper jsonMapper, LlmRetry retry) {
        this.restClient = restClient;
        this.settings = settings;
        this.jsonMapper = jsonMapper;
        this.retry = retry;
    }

    @Override
    public LlmAnswer read(List<Segment> segments) {
        OpenAiChatRequest request = new OpenAiChatRequest(
                settings.model(),
                settings.reasoningEffort(),
                List.of(new OpenAiChatRequest.Message("system", LlmPrompt.SYSTEM),
                        new OpenAiChatRequest.Message("user", LlmPrompt.userMessage(segments))),
                responseFormat());

        OpenAiChatResponse response = retry.call(() -> restClient.post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .body(OpenAiChatResponse.class));

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new LlmDocumentException(LlmDocumentException.Reason.INVALID_ANSWER, "OpenAI 응답에 선택지가 없습니다");
        }
        OpenAiChatResponse.Choice choice = response.choices().get(0);
        if ("length".equals(choice.finishReason())) {
            throw new LlmDocumentException(LlmDocumentException.Reason.TRUNCATED, "출력 길이 상한에 걸려 답이 잘렸습니다");
        }
        if (choice.message() == null) {
            throw new LlmDocumentException(LlmDocumentException.Reason.INVALID_ANSWER, "OpenAI 응답에 메시지가 없습니다");
        }
        String refusal = choice.message().refusal();
        if (refusal != null && !refusal.isBlank()) {
            throw new LlmDocumentException(LlmDocumentException.Reason.REFUSED, "모델이 답하기를 거절했습니다: " + refusal);
        }
        return LlmAnswerParser.parse(jsonMapper, choice.message().content());
    }

    @Override
    public String modelName() {
        return settings.model();
    }

    @Override
    public String promptVersion() {
        return LlmPrompt.VERSION;
    }

    private static Map<String, Object> responseFormat() {
        Map<String, Object> jsonSchema = new LinkedHashMap<>();
        jsonSchema.put("name", SCHEMA_NAME);
        jsonSchema.put("strict", true);
        jsonSchema.put("schema", LlmPrompt.schema());

        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "json_schema");
        responseFormat.put("json_schema", jsonSchema);
        return responseFormat;
    }
}
