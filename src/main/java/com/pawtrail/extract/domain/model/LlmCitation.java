package com.pawtrail.extract.domain.model;

import java.util.List;

/**
 * 모델이 댄 근거 한 줄입니다 — 칸 이름과 그 값을 말한 조각 번호들.
 *
 * 번호는 아직 검사하기 전이라 범위 밖일 수 있습니다. 근거 검사가 거릅니다.
 */
public record LlmCitation(
        String fieldName,
        List<Integer> segments
) {

    public LlmCitation {
        segments = segments == null ? List.of() : List.copyOf(segments);
    }
}
