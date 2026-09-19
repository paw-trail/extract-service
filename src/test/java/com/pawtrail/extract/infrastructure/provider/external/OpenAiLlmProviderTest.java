package com.pawtrail.extract.infrastructure.provider.external;

import com.pawtrail.extract.domain.enums.BreedRule;
import com.pawtrail.extract.domain.exception.LlmDocumentException;
import com.pawtrail.extract.domain.exception.LlmUnavailableException;
import com.pawtrail.extract.domain.model.LlmAnswer;
import com.pawtrail.extract.domain.model.Segment;
import com.pawtrail.extract.infrastructure.config.LlmProperties;
import com.pawtrail.extract.support.ModelAnswers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 응답을 흉내 낸 OpenAI 로 요청 모양과 실패 갈래를 봅니다. 실제 API 는 부르지 않습니다.
 */
class OpenAiLlmProviderTest {

    private static final String BASE = "https://openai.test/v1";
    private static final String COMPLETIONS = BASE + "/chat/completions";
    private static final LlmProperties.OpenAi SETTINGS =
            new LlmProperties.OpenAi(BASE, "gpt-5.6-luna", "low", null, "test-key", 60);
    private static final LlmProperties.OpenAi TWICE =
            new LlmProperties.OpenAi(BASE, "gpt-5.6-luna", "medium", "high", "test-key", 60);
    private static final List<Segment> SEGMENTS = List.of(
            new Segment(1, "반려동물 제한사항", null, "맹견류 입장 불가"));

    private final List<Long> waits = new ArrayList<>();
    private MockRestServiceServer server;
    private OpenAiLlmProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new OpenAiLlmProvider(builder.build(), SETTINGS, JsonMapper.builder().build(),
                new LlmRetry(3, 1000, waits::add));
    }

    @Test
    @DisplayName("응답 스키마를 json_schema strict 로 싣고 온도는 보내지 않는다")
    void 요청_모양() {
        server.expect(requestTo(COMPLETIONS))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("gpt-5.6-luna"))
                .andExpect(jsonPath("$.reasoning_effort").value("low"))
                .andExpect(jsonPath("$.temperature").doesNotExist())
                .andExpect(jsonPath("$.response_format.type").value("json_schema"))
                .andExpect(jsonPath("$.response_format.json_schema.name").value(OpenAiLlmProvider.SCHEMA_NAME))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andExpect(jsonPath("$.response_format.json_schema.schema.required[0]").value("fields"))
                .andExpect(jsonPath("$.messages[0].content").value(LlmPrompt.SYSTEM))
                .andExpect(jsonPath("$.messages[1].content").value("원문 조각\n[1] (반려동물 제한사항) 맹견류 입장 불가"))
                .andRespond(withSuccess(response(ModelAnswers.answer()
                        .value("breedRule", "DANGEROUS_BANNED").cite("breedRule", 1).json(), "stop", null),
                        MediaType.APPLICATION_JSON));

        LlmAnswer answer = provider.read(SEGMENTS);

        server.verify();
        assertThat(answer.fields().breedRule()).isEqualTo(BreedRule.DANGEROUS_BANNED);
    }

    @Test
    @DisplayName("두 번째 추론 강도가 없으면 한 번만 읽고 모델 이름도 그대로다")
    void 한_번_읽기() {
        assertThat(provider.readSecond(SEGMENTS)).isEmpty();
        assertThat(provider.modelName()).isEqualTo("gpt-5.6-luna");
        server.verify();
    }

    @Test
    @DisplayName("두 번째 읽기는 모델 · 프롬프트는 그대로 두고 추론 강도만 바꿔 부른다")
    void 두_번째_읽기() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer twiceServer = MockRestServiceServer.bindTo(builder).build();
        OpenAiLlmProvider twice = new OpenAiLlmProvider(builder.build(), TWICE, JsonMapper.builder().build(),
                new LlmRetry(3, 1000, waits::add));
        twiceServer.expect(requestTo(COMPLETIONS))
                .andExpect(jsonPath("$.model").value("gpt-5.6-luna"))
                .andExpect(jsonPath("$.reasoning_effort").value("high"))
                .andExpect(jsonPath("$.messages[1].content").value("원문 조각\n[1] (반려동물 제한사항) 맹견류 입장 불가"))
                .andRespond(withSuccess(response(ModelAnswers.answer()
                        .value("breedRule", "DANGEROUS_BANNED").cite("breedRule", 1).json(), "stop", null),
                        MediaType.APPLICATION_JSON));

        assertThat(twice.readSecond(SEGMENTS)).isPresent();
        // policy 의 추출 기록에 두 번 읽은 행인지 남고, 평가 보고서도 한 번 읽기 것을 덮지 않음
        assertThat(twice.modelName()).isEqualTo("gpt-5.6-luna medium+high");
        twiceServer.verify();
    }

    @Test
    @DisplayName("출력 길이 상한에 걸려 잘리면 그 문서만 실패다")
    void 잘림() {
        server.expect(requestTo(COMPLETIONS)).andRespond(withSuccess(
                response("{\"fields\":", "length", null), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.read(SEGMENTS))
                .isInstanceOf(LlmDocumentException.class)
                .extracting(e -> ((LlmDocumentException) e).reason())
                .isEqualTo(LlmDocumentException.Reason.TRUNCATED);
    }

    @Test
    @DisplayName("모델이 답하기를 거절하면 그 문서만 실패다")
    void 거절() {
        server.expect(requestTo(COMPLETIONS)).andRespond(withSuccess(
                response(null, "stop", "I can't help with that."), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.read(SEGMENTS))
                .isInstanceOf(LlmDocumentException.class)
                .extracting(e -> ((LlmDocumentException) e).reason())
                .isEqualTo(LlmDocumentException.Reason.REFUSED);
    }

    @Test
    @DisplayName("인증 실패(401)는 재시도 없이 바로 멈춘다")
    void 인증_실패() {
        server.expect(ExpectedCount.once(), requestTo(COMPLETIONS)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> provider.read(SEGMENTS)).isInstanceOf(LlmUnavailableException.class);

        server.verify();
        assertThat(waits).isEmpty();
    }

    @Test
    @DisplayName("요청 한도(429)가 이어지면 세 번 재시도한 뒤 멈춘다")
    void 요청_한도() {
        server.expect(ExpectedCount.times(4), requestTo(COMPLETIONS)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> provider.read(SEGMENTS)).isInstanceOf(LlmUnavailableException.class);

        server.verify();
        assertThat(waits).containsExactly(1000L, 2000L, 4000L);
    }

    private static String response(String content, String finishReason, String refusal) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", content);
        message.put("refusal", refusal);
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", finishReason);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", "chatcmpl-test");
        body.put("object", "chat.completion");
        body.put("choices", List.of(choice));
        return ModelAnswers.toJson(body);
    }
}
