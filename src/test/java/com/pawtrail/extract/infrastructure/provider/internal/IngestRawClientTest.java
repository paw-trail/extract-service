package com.pawtrail.extract.infrastructure.provider.internal;

import com.pawtrail.extract.domain.enums.SourceType;
import com.pawtrail.extract.domain.exception.InternalCallException;
import com.pawtrail.extract.domain.model.PendingDocument;
import com.pawtrail.extract.domain.model.PendingDocuments;
import com.pawtrail.extract.domain.model.StatusMark;
import com.pawtrail.extract.domain.model.StatusResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * ingest 의 두 경로를 흉내 서버로 부르며 요청 모양 · 응답 읽기 · 약속 검사를 봅니다.
 */
class IngestRawClientTest {

    private static final String BASE = "http://ingest";
    private static final UUID DOC_ID = UUID.fromString("01a082a8-c887-7ff8-a772-0307c4b61794");
    private static final UUID PLACE_ID = UUID.fromString("01a09015-a663-7661-99c6-0b19bdab187c");

    private MockRestServiceServer server;
    private IngestRawClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new IngestRawClient(builder.build());
    }

    @Test
    @DisplayName("대기 원문을 첫 쪽으로 받아 장소 · 원본 · 해시를 옮긴다")
    void 원문_목록() {
        server.expect(requestTo(BASE + "/internal/raw?status=PENDING&size=100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(envelope("""
                        {"total":17463,"documents":[
                          {"id":"%s","source":"PET_TOUR","sourceId":"2784167","placeId":"%s",
                           "payload":{"petTour":{"acmpyTypeCd":"일부구역 동반가능"}},
                           "contentHash":"4cc5675b","sourceModified":"2025-04-17T09:21:52"}]}
                        """.formatted(DOC_ID, PLACE_ID)), MediaType.APPLICATION_JSON));

        PendingDocuments page = client.findPending(100);

        assertThat(page.total()).isEqualTo(17463);
        PendingDocument document = page.documents().getFirst();
        assertThat(document.id()).isEqualTo(DOC_ID);
        assertThat(document.source()).isEqualTo(SourceType.PET_TOUR);
        assertThat(document.placeId()).isEqualTo(PLACE_ID);
        assertThat(document.contentHash()).isEqualTo("4cc5675b");
        assertThat(document.payload()).containsKey("petTour");
        server.verify();
    }

    @Test
    @DisplayName("장소가 빈 원문이 오면 고쳐 쓰지 않고 멈춘다")
    void 장소가_빈_원문() {
        server.expect(requestTo(BASE + "/internal/raw?status=PENDING&size=100"))
                .andRespond(withSuccess(envelope("""
                        {"total":1,"documents":[
                          {"id":"%s","source":"GOCAMPING","sourceId":"1","placeId":null,
                           "payload":{},"contentHash":"h"}]}
                        """.formatted(DOC_ID)), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findPending(100)).isInstanceOf(InternalCallException.class);
    }

    @Test
    @DisplayName("ingest 가 답하지 못하면 멈춘다")
    void 목록_실패() {
        server.expect(requestTo(BASE + "/internal/raw?status=PENDING&size=100"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.findPending(100)).isInstanceOf(InternalCallException.class);
    }

    @Test
    @DisplayName("처리 결과를 식별자와 내용 해시로 되돌려 쓴다")
    void 상태_갱신() {
        UUID failedId = UUID.randomUUID();
        server.expect(requestTo(BASE + "/internal/raw/status"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(jsonPath("$.done[0].id").value(DOC_ID.toString()))
                .andExpect(jsonPath("$.done[0].contentHash").value("h1"))
                .andExpect(jsonPath("$.failed[0].id").value(failedId.toString()))
                .andExpect(jsonPath("$.failed[0].contentHash").value("h2"))
                .andRespond(withSuccess(envelope("{\"updated\":1,\"skipped\":1}"), MediaType.APPLICATION_JSON));

        StatusResult result = client.markStatus(
                List.of(new StatusMark(DOC_ID, "h1")), List.of(new StatusMark(failedId, "h2")));

        assertThat(result).isEqualTo(new StatusResult(1, 1));
        server.verify();
    }

    @Test
    @DisplayName("되돌려 쓴 건수가 보낸 수와 다르면 멈춘다")
    void 상태_갱신_건수_어긋남() {
        server.expect(requestTo(BASE + "/internal/raw/status"))
                .andRespond(withSuccess(envelope("{\"updated\":0,\"skipped\":0}"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.markStatus(List.of(new StatusMark(DOC_ID, "h1")), List.of()))
                .isInstanceOf(InternalCallException.class)
                .hasMessageContaining("보낸 수와 다릅니다");
    }

    private static String envelope(String data) {
        return "{\"code\":\"SUCCESS\",\"message\":\"ok\",\"data\":" + data + ",\"traceId\":\"t\"}";
    }
}
