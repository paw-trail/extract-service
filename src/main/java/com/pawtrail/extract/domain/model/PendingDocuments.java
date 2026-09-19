package com.pawtrail.extract.domain.model;

import java.util.List;

/**
 * 처리 대기 원문 한 쪽입니다.
 *
 * <pre>
 * total       장소에 이어진 대기 원문이 모두 몇 건인지 — 진행률을 찍는 값
 * documents   이번에 가져온 것 — 오래된 것부터
 * </pre>
 *
 * 언제나 첫 쪽을 가져갑니다. 처리하면 대기에서 빠지고 다음 것이 올라오기 때문입니다.
 */
public record PendingDocuments(long total, List<PendingDocument> documents) {

    public PendingDocuments {
        documents = List.copyOf(documents);
    }
}
