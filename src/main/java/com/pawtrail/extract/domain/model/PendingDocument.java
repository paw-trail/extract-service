package com.pawtrail.extract.domain.model;

import com.pawtrail.extract.domain.enums.SourceType;

import java.util.Map;
import java.util.UUID;

/**
 * ingest 가 준 처리 대기 원문 한 건입니다.
 *
 * <pre>
 * id           원문 식별자 — 처리 결과를 되돌려 쓸 때 그대로 보냄
 * source       소스
 * sourceId     소스가 붙인 식별자 — 로그에서 사람이 알아보는 값
 * placeId      이 원문이 이어진 장소 — 조건을 장소 단위로 policy 에 보냄
 *              ingest 가 장소에 이어진 원문만 주므로 늘 값이 있음
 * payload      소스 응답 원본 — 이미 풀린 Map
 * contentHash  내용 해시 — 되돌려 쓸 때 함께 보냄
 * </pre>
 */
public record PendingDocument(
        UUID id,
        SourceType source,
        String sourceId,
        UUID placeId,
        Map<String, Object> payload,
        String contentHash
) {

    /**
     * 되돌려 쓸 때 보낼 한 줄입니다.
     */
    public StatusMark mark() {
        return new StatusMark(id, contentHash);
    }
}
