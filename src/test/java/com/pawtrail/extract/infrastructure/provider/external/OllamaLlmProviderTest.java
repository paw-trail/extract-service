package com.pawtrail.extract.infrastructure.provider.external;

import com.pawtrail.extract.domain.enums.SizeRule;
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

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 응답을 흉내 낸 Ollama 로 요청 모양과 실패 갈래를 봅니다. 실제 모델은 부르지 않습니다.
 */
class OllamaLlmProviderTest {

    private static final String BASE = "http://ollama.test";
    private static final String CHAT = BASE + "/api/chat";
    private static final LlmProperties.Ollama SETTINGS =
            new LlmProperties.Ollama(BASE, "qwen3.8:27b", false, 16384, 2048, 0, 42, 120);
    private static final List<Segment> SEGMENTS = List.of(
            new Segment(1, "acmpyPsblCpam", null, "전 견종 동반 가능"),
            new Segment(2, "acmpyNeedMtr", 1, "목줄 착용"));

    private final List<Long> waits = new ArrayList<>();
    private MockRestServiceServer server;
    private OllamaLlmProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new OllamaLlmProvider(builder.build(), SETTINGS, JsonMapper.builder().build(),
                new LlmRetry(3, 1000, waits::add));
    }

    @Test
    @DisplayName("네이티브 /api/chat 에 스키마 · 생각 끔 · 온도 0 · seed · 문맥 · 출력 길이를 싣는다")
    void 요청_모양() {
        server.expect(requestTo(CHAT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("qwen3.8:27b"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.think").value(false))
                .andExpect(jsonPath("$.options.temperature").value(0.0))
                .andExpect(jsonPath("$.options.seed").value(42))
                .andExpect(jsonPath("$.options.num_ctx").value(16384))
                .andExpect(jsonPath("$.options.num_predict").value(2048))
                .andExpect(jsonPath("$.format.required[0]").value("fields"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value(LlmPrompt.SYSTEM))
                .andExpect(jsonPath("$.messages[1].content")
                        .value("원문 조각\n[1] (동반 가능 동물) 전 견종 동반 가능\n[2] (필요사항) 목줄 착용"))
                .andRespond(withSuccess(response(ModelAnswers.answer()
                        .value("sizeRule", "ALL").cite("sizeRule", 1).json(), "stop", 900), MediaType.APPLICATION_JSON));

        LlmAnswer answer = provider.read(SEGMENTS);

        server.verify();
        assertThat(answer.fields().sizeRule()).isEqualTo(SizeRule.ALL);
        assertThat(answer.citations()).hasSize(1);
    }

    @Test
    @DisplayName("출력 길이 상한에 걸려 잘리면 그 문서만 실패다")
    void 잘림() {
        server.expect(requestTo(CHAT)).andRespond(withSuccess(
                response("{\"fields\":{\"scope\":", "length", 900), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.read(SEGMENTS))
                .isInstanceOf(LlmDocumentException.class)
                .extracting(e -> ((LlmDocumentException) e).reason())
                .isEqualTo(LlmDocumentException.Reason.TRUNCATED);
    }

    @Test
    @DisplayName("입력이 문맥 길이를 채우면 그 문서만 실패다")
    void 입력_넘침() {
        server.expect(requestTo(CHAT)).andRespond(withSuccess(
                response(ModelAnswers.answer().json(), "stop", 16384), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.read(SEGMENTS))
                .isInstanceOf(LlmDocumentException.class)
                .extracting(e -> ((LlmDocumentException) e).reason())
                .isEqualTo(LlmDocumentException.Reason.INPUT_TOO_LONG);
    }

    @Test
    @DisplayName("답이 JSON 이 아니면 그 문서만 실패다")
    void 형식_어긋남() {
        server.expect(requestTo(CHAT)).andRespond(withSuccess(
                response("조건을 찾지 못했습니다", "stop", 900), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.read(SEGMENTS))
                .isInstanceOf(LlmDocumentException.class)
                .extracting(e -> ((LlmDocumentException) e).reason())
                .isEqualTo(LlmDocumentException.Reason.INVALID_ANSWER);
    }

    @Test
    @DisplayName("과부하로 한 번 실패한 뒤 성공하면 그 답을 쓴다")
    void 한_번_뒤_성공() {
        server.expect(requestTo(CHAT)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(CHAT)).andRespond(withSuccess(
                response(ModelAnswers.answer().json(), "stop", 900), MediaType.APPLICATION_JSON));

        LlmAnswer answer = provider.read(SEGMENTS);

        server.verify();
        assertThat(answer.fields().isEmpty()).isTrue();
        assertThat(waits).containsExactly(1000L);
    }

    @Test
    @DisplayName("5xx 가 이어지면 세 번 재시도한 뒤 실행을 멈춘다")
    void 계속_5xx() {
        server.expect(ExpectedCount.times(4), requestTo(CHAT)).andRespond(withServerError());

        assertThatThrownBy(() -> provider.read(SEGMENTS)).isInstanceOf(LlmUnavailableException.class);

        server.verify();
        assertThat(waits).containsExactly(1000L, 2000L, 4000L);
    }

    @Test
    @DisplayName("제한시간이 이어져도 세 번 재시도한 뒤 멈춘다")
    void 계속_제한시간() {
        server.expect(ExpectedCount.times(4), requestTo(CHAT))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> provider.read(SEGMENTS)).isInstanceOf(LlmUnavailableException.class);

        server.verify();
        assertThat(waits).hasSize(3);
    }

    @Test
    @DisplayName("없는 모델(404)은 재시도 없이 바로 멈춘다")
    void 없는_모델() {
        server.expect(ExpectedCount.once(), requestTo(CHAT)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> provider.read(SEGMENTS)).isInstanceOf(LlmUnavailableException.class);

        server.verify();
        assertThat(waits).isEmpty();
    }

    @Test
    @DisplayName("추출 기록에 남길 모델 이름과 프롬프트 판")
    void 기록_값() {
        assertThat(provider.modelName()).isEqualTo("qwen3.8:27b");
        assertThat(provider.promptVersion()).isEqualTo(LlmPrompt.VERSION);
    }

    // 실제 응답처럼 읽지 않는 칸(created_at · total_duration)도 함께 싣음
    private static String response(String content, String doneReason, int promptEvalCount) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", content);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "qwen3.8:27b");
        body.put("created_at", "2026-09-18T12:00:00Z");
        body.put("message", message);
        body.put("done", true);
        body.put("done_reason", doneReason);
        body.put("total_duration", 1_500_000_000L);
        body.put("prompt_eval_count", promptEvalCount);
        body.put("eval_count", 150);
        return ModelAnswers.toJson(body);
    }
}
