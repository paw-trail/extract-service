package com.pawtrail.extract.infrastructure.provider.internal.dto;

import com.pawtrail.extract.domain.enums.SourceType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ingest GET /internal/raw 의 data 입니다.
 *
 * 응답에는 소스 수정 시각도 실리나 extract 가 쓰지 않아 받지 않습니다. 모르는 칸은 건너뜁니다.
 *
 * @param total     장소에 이어진 대기 원문이 모두 몇 건인지
 * @param documents 이번에 가져온 원문
 */
public record RawDocumentResponse(long total, List<Document> documents) {

    public record Document(
            UUID id,
            SourceType source,
            String sourceId,
            UUID placeId,
            Map<String, Object> payload,
            String contentHash
    ) {
    }
}
