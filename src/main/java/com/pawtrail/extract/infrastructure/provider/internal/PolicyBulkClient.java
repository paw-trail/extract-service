package com.pawtrail.extract.infrastructure.provider.internal;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.extract.domain.exception.InternalCallException;
import com.pawtrail.extract.domain.model.BulkResult;
import com.pawtrail.extract.domain.model.PolicyItem;
import com.pawtrail.extract.domain.provider.PolicyProvider;
import com.pawtrail.extract.infrastructure.provider.internal.dto.PolicyBulkRequest;
import com.pawtrail.extract.infrastructure.provider.internal.dto.PolicyBulkResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * policy 의 /internal/policies/bulk 로 한 청크의 조건을 보냅니다.
 *
 * <b>400 이면 응답 본문을 예외 메시지에 싣습니다.</b>
 * policy 는 어느 항목의 어느 칸이 걸렸는지를 본문의 data 에 담아 돌려줍니다.
 * 보내기 전에 같은 규칙을 PolicyItemCheck 로 먼저 보지만, 거기서 놓친 것이 여기서 드러나고
 * 그 본문이 로그에 남아야 무엇을 고칠지 압니다.
 *
 * 받은 수가 보낸 수와 다르면 멈춥니다. 일부만 저장된 채 원문을 처리 완료로 바꾸면
 * 저장되지 않은 조건이 다시 뽑히지 않습니다.
 */
public class PolicyBulkClient implements PolicyProvider {

    // 로그에 남길 응답 본문의 길이 — 400 본문이 길어도 한 줄이 로그를 덮지 않게
    private static final int BODY_LOG_MAX = 1000;

    private final RestClient restClient;

    public PolicyBulkClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public BulkResult bulk(String extractedBy, String promptVersion, LocalDateTime extractedAt, List<PolicyItem> items) {
        PolicyBulkResponse result;
        try {
            CommonApiResponse<PolicyBulkResponse> response = restClient.post()
                    .uri("/internal/policies/bulk")
                    .body(PolicyBulkRequest.of(extractedBy, promptVersion, extractedAt, items))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            result = response == null ? null : response.getData();
        } catch (RestClientResponseException e) {
            throw new InternalCallException("policy 가 bulk 를 받지 않았습니다 — " + e.getStatusCode().value()
                    + " " + abbreviate(e.getResponseBodyAsString()), e);
        } catch (RuntimeException e) {
            throw new InternalCallException("policy 에 bulk 를 보내지 못했습니다", e);
        }
        if (result == null) {
            throw new InternalCallException("policy bulk 응답에 data 가 없습니다");
        }
        if (result.accepted() != items.size()) {
            throw new InternalCallException("policy 가 받은 항목 수가 보낸 수와 다릅니다 — 보냄 " + items.size()
                    + " · 받음 " + result.accepted());
        }
        return new BulkResult(result.accepted(), result.merged());
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= BODY_LOG_MAX ? body : body.substring(0, BODY_LOG_MAX) + "…";
    }
}
