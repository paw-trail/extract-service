package com.pawtrail.extract.infrastructure.provider.external;

import com.pawtrail.extract.domain.exception.LlmDocumentException;
import com.pawtrail.extract.domain.model.LlmAnswer;
import com.pawtrail.extract.domain.model.Segment;
import com.pawtrail.extract.domain.provider.LlmProvider;
import com.pawtrail.extract.infrastructure.config.LlmProperties;
import com.pawtrail.extract.infrastructure.provider.external.dto.OllamaChatRequest;
import com.pawtrail.extract.infrastructure.provider.external.dto.OllamaChatResponse;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 로컬 Ollama(네이티브 /api/chat)로 조건을 읽습니다.
 *
 * 빈은 LlmConfig 가 만듭니다. 주소 · 제한시간을 붙인 RestClient 를 밖에서 받는 이유는
 * 테스트가 응답을 흉내 낸 RestClient 를 넣을 수 있게 하려는 것입니다.
 * 생성자에서 요청 팩토리를 붙이면 흉내 서버가 심어 둔 팩토리를 덮어써 요청이 진짜로 나갑니다.
 *
 * <b>응답을 보고 그 문서만 실패로 두는 경우</b>
 * <pre>
 * done_reason 이 length            출력 길이 상한(num_predict)에 걸려 답이 잘림
 * prompt_eval_count ≥ num_ctx      입력이 문맥 길이를 채움 — 앞부분이 잘렸을 수 있음
 * 답이 JSON 이 아니거나 모양이 다름   LlmAnswerParser 가 가름
 * </pre>
 * 실측상 가장 긴 입력이 3천 자 남짓이라 문맥 16384 에 한참 못 미칩니다. 그래도 조용히 잘린 입력으로
 * 뽑은 조건을 쓰지 않도록 막아 둡니다.
 */
public class OllamaLlmProvider implements LlmProvider {

    private final RestClient restClient;
    private final LlmProperties.Ollama settings;
    private final JsonMapper jsonMapper;
    private final LlmRetry retry;

    public OllamaLlmProvider(RestClient restClient, LlmProperties.Ollama settings,
                             JsonMapper jsonMapper, LlmRetry retry) {
        this.restClient = restClient;
        this.settings = settings;
        this.jsonMapper = jsonMapper;
        this.retry = retry;
    }

    @Override
    public LlmAnswer read(List<Segment> segments) {
        OllamaChatRequest request = new OllamaChatRequest(
                settings.model(),
                false,
                settings.think(),
                LlmPrompt.schema(),
                List.of(new OllamaChatRequest.Message("system", LlmPrompt.SYSTEM),
                        new OllamaChatRequest.Message("user", LlmPrompt.userMessage(segments))),
                new OllamaChatRequest.Options(settings.temperature(), settings.seed(),
                        settings.numCtx(), settings.numPredict()));

        OllamaChatResponse response = retry.call(() -> restClient.post()
                .uri("/api/chat")
                .body(request)
                .retrieve()
                .body(OllamaChatResponse.class));

        if (response == null || response.message() == null) {
            throw new LlmDocumentException(LlmDocumentException.Reason.INVALID_ANSWER, "Ollama 응답에 메시지가 없습니다");
        }
        if ("length".equals(response.doneReason())) {
            throw new LlmDocumentException(LlmDocumentException.Reason.TRUNCATED,
                    "출력 길이 상한(num_predict " + settings.numPredict() + ")에 걸려 답이 잘렸습니다");
        }
        if (response.promptEvalCount() != null && response.promptEvalCount() >= settings.numCtx()) {
            throw new LlmDocumentException(LlmDocumentException.Reason.INPUT_TOO_LONG,
                    "입력이 문맥 길이(num_ctx " + settings.numCtx() + ")를 채웠습니다: " + response.promptEvalCount());
        }
        return LlmAnswerParser.parse(jsonMapper, response.message().content());
    }

    @Override
    public String modelName() {
        return settings.model();
    }

    @Override
    public String promptVersion() {
        return LlmPrompt.VERSION;
    }
}
