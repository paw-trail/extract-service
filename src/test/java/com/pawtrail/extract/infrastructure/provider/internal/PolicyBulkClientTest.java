package com.pawtrail.extract.infrastructure.provider.internal;

import com.pawtrail.extract.domain.enums.ExtractionMethod;
import com.pawtrail.extract.domain.enums.Scope;
import com.pawtrail.extract.domain.enums.SourceType;
import com.pawtrail.extract.domain.exception.InternalCallException;
import com.pawtrail.extract.domain.model.BulkResult;
import com.pawtrail.extract.domain.model.ConditionFields;
import com.pawtrail.extract.domain.model.Evidence;
import com.pawtrail.extract.domain.model.FieldNames;
import com.pawtrail.extract.domain.model.IntraConflict;
import com.pawtrail.extract.domain.model.PolicyItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * policy bulk 요청이 policy 의 BulkUpsertRequest 모양대로 나가는지와 실패 처리를 봅니다.
 */
class PolicyBulkClientTest {

    private static final String BASE = "http://policy";
    private static final UUID PLACE_ID = UUID.fromString("01a09015-b6bc-7812-8e7e-d0c59c46b007");
    private static final LocalDateTime STARTED = LocalDateTime.of(2026, 9, 18, 12, 0);

    private MockRestServiceServer server;
    private PolicyBulkClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PolicyBulkClient(builder.build());
    }

    @Test
    @DisplayName("조건 20칸 · 근거 · 충돌 · 추출 방식을 policy 의 요청 모양으로 보낸다")
    void 요청_모양() {
        server.expect(requestTo(BASE + "/internal/policies/bulk"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.extractedBy").value("gpt-5.6-luna"))
                .andExpect(jsonPath("$.promptVersion").value("v2"))
                .andExpect(jsonPath("$.extractedAt").value("2026-09-18T12:00:00"))
                .andExpect(jsonPath("$.items[0].placeId").value(PLACE_ID.toString()))
                .andExpect(jsonPath("$.items[0].source").value("GOCAMPING"))
                .andExpect(jsonPath("$.items[0].fields.scope").value("NONE"))
                .andExpect(jsonPath("$.items[0].fields.indoorAllowed").value(false))
                // 비어 있는 칸은 null 로 나가 "정보 없음" 이 됨
                .andExpect(jsonPath("$.items[0].fields.sizeRule").value(nullValue()))
                .andExpect(jsonPath("$.items[0].evidence[0].fieldName").value("scope"))
                .andExpect(jsonPath("$.items[0].evidence[0].originField").value("animalCmgCl"))
                .andExpect(jsonPath("$.items[0].evidence[0].segmentText").value("불가능"))
                // 근거 줄마다 읽은 쪽 — policy v0.1.2 부터 필수
                .andExpect(jsonPath("$.items[0].evidence[0].extractionMethod").value("RULE"))
                .andExpect(jsonPath("$.items[0].conflicts[0].fieldName").value("scope"))
                .andExpect(jsonPath("$.items[0].conflicts[0].sourceValues.field").value("불가능"))
                .andExpect(jsonPath("$.items[0].conflicts[0].sourceValues.text").value("소형견만 출입 허용"))
                .andExpect(jsonPath("$.items[0].extractionMethod").value("MIXED"))
                .andRespond(withSuccess(envelope("{\"accepted\":1,\"merged\":1}"), MediaType.APPLICATION_JSON));

        BulkResult result = client.bulk("gpt-5.6-luna", "v2", STARTED, List.of(item()));

        assertThat(result).isEqualTo(new BulkResult(1, 1));
        server.verify();
    }

    @Test
    @DisplayName("400 이면 policy 가 돌려준 본문을 메시지에 싣고 멈춘다")
    void 검증_실패() {
        server.expect(requestTo(BASE + "/internal/policies/bulk"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"VALIDATION_FAILED\",\"data\":[{\"field\":\"items[0].fields.maxCount\"}]}"));

        assertThatThrownBy(() -> client.bulk("gpt-5.6-luna", "v2", STARTED, List.of(item())))
                .isInstanceOf(InternalCallException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("items[0].fields.maxCount");
    }

    @Test
    @DisplayName("받은 수가 보낸 수와 다르면 멈춘다")
    void 받은_수_어긋남() {
        server.expect(requestTo(BASE + "/internal/policies/bulk"))
                .andRespond(withSuccess(envelope("{\"accepted\":0,\"merged\":0}"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.bulk("gpt-5.6-luna", "v2", STARTED, List.of(item())))
                .isInstanceOf(InternalCallException.class)
                .hasMessageContaining("보낸 수와 다릅니다");
    }

    private static PolicyItem item() {
        return new PolicyItem(PLACE_ID, SourceType.GOCAMPING,
                ConditionFields.builder().scope(Scope.NONE).indoorAllowed(false).outdoorAllowed(false).build(),
                List.of(Evidence.ofRule(FieldNames.SCOPE, "animalCmgCl", "불가능")),
                List.of(new IntraConflict(FieldNames.SCOPE, "불가능", "소형견만 출입 허용")),
                ExtractionMethod.MIXED);
    }

    private static String envelope(String data) {
        return "{\"code\":\"SUCCESS\",\"message\":\"ok\",\"data\":" + data + ",\"traceId\":\"t\"}";
    }
}
