package com.pawtrail.extract.infrastructure.provider.internal;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.extract.domain.exception.InternalCallException;
import com.pawtrail.extract.domain.model.PendingDocument;
import com.pawtrail.extract.domain.model.PendingDocuments;
import com.pawtrail.extract.domain.model.StatusMark;
import com.pawtrail.extract.domain.model.StatusResult;
import com.pawtrail.extract.domain.provider.RawDocumentProvider;
import com.pawtrail.extract.infrastructure.provider.internal.dto.RawDocumentResponse;
import com.pawtrail.extract.infrastructure.provider.internal.dto.RawStatusRequest;
import com.pawtrail.extract.infrastructure.provider.internal.dto.RawStatusResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * ingest 의 /internal/raw 두 경로로 원문을 가져오고 처리 결과를 되돌려 씁니다.
 *
 * <b>실패는 값으로 돌려주지 않고 InternalCallException 을 던집니다.</b>
 * 상대가 한 서비스라 한 번 실패하면 다음 청크도 같은 이유로 실패합니다.
 * 실행이 그 자리에서 멈추고, 원문 상태가 그대로라 다음 실행이 같은 원문부터 다시 가져갑니다.
 * 연결 거부 · 시간 초과 · 서비스를 못 찾음 · 응답 모양이 다름까지 할 일이 같아 넓게 잡습니다.
 *
 * <b>응답이 약속대로인지도 봅니다.</b>
 * 원문마다 식별자 · 장소 · 내용 해시가 있어야 하고, 되돌려 쓴 결과는 보낸 수와 맞아야 합니다.
 * 어긋나면 고쳐서 쓰지 않고 멈춥니다. 장소 없는 원문은 보낼 곳이 없고, 해시 없는 원문은
 * 되돌려 쓸 수가 없습니다.
 */
public class IngestRawClient implements RawDocumentProvider {

    private static final String PENDING = "PENDING";

    private final RestClient restClient;

    public IngestRawClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public PendingDocuments findPending(int size) {
        RawDocumentResponse page;
        try {
            CommonApiResponse<RawDocumentResponse> response = restClient.get()
                    .uri(uri -> uri.path("/internal/raw")
                            .queryParam("status", PENDING)
                            .queryParam("size", size)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            page = response == null ? null : response.getData();
        } catch (RuntimeException e) {
            throw new InternalCallException("ingest 에서 원문 목록을 가져오지 못했습니다", e);
        }
        if (page == null || page.documents() == null) {
            throw new InternalCallException("ingest 원문 목록 응답에 data 가 없습니다");
        }
        List<PendingDocument> documents = page.documents().stream()
                .map(IngestRawClient::verified)
                .toList();
        return new PendingDocuments(page.total(), documents);
    }

    @Override
    public StatusResult markStatus(List<StatusMark> done, List<StatusMark> failed) {
        RawStatusResponse result;
        try {
            CommonApiResponse<RawStatusResponse> response = restClient.patch()
                    .uri("/internal/raw/status")
                    .body(new RawStatusRequest(done, failed))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            result = response == null ? null : response.getData();
        } catch (RuntimeException e) {
            throw new InternalCallException("ingest 에 처리 결과를 되돌려 쓰지 못했습니다", e);
        }
        if (result == null) {
            throw new InternalCallException("ingest 상태 갱신 응답에 data 가 없습니다");
        }
        int sent = distinctCount(done, failed);
        if (result.updated() + result.skipped() != sent) {
            throw new InternalCallException("ingest 상태 갱신 건수가 보낸 수와 다릅니다 — 보냄 " + sent
                    + " · 바꿈 " + result.updated() + " · 건너뜀 " + result.skipped());
        }
        return new StatusResult(result.updated(), result.skipped());
    }

    private static PendingDocument verified(RawDocumentResponse.Document document) {
        if (document.id() == null || document.source() == null || document.placeId() == null
                || document.payload() == null || document.contentHash() == null || document.contentHash().isBlank()) {
            throw new InternalCallException("ingest 원문 목록에 약속한 칸이 빈 원문이 있습니다 — id " + document.id()
                    + " · 장소 " + document.placeId());
        }
        return new PendingDocument(document.id(), document.source(), document.sourceId(),
                document.placeId(), document.payload(), document.contentHash());
    }

    // ingest 도 같은 식별자를 한 번으로 셈
    private static int distinctCount(List<StatusMark> done, List<StatusMark> failed) {
        Set<UUID> ids = new HashSet<>();
        Stream.concat(done.stream(), failed.stream()).map(StatusMark::id).forEach(ids::add);
        return ids.size();
    }
}
