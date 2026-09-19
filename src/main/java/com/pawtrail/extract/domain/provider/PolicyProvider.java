package com.pawtrail.extract.domain.provider;

import com.pawtrail.extract.domain.model.BulkResult;
import com.pawtrail.extract.domain.model.PolicyItem;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 뽑은 조건을 policy 에 한 묶음씩 보냅니다.
 *
 * 구현은 infrastructure/provider/internal 에 있고 /internal/policies/bulk 를 부릅니다.
 */
public interface PolicyProvider {

    /**
     * @param extractedBy   모델 이름 — 추출 기록에 남음
     * @param promptVersion 프롬프트 판 — 추출 기록에 남음
     * @param extractedAt   실행을 시작한 시각 — 한 실행이 값 하나로 묶임
     * @throws com.pawtrail.extract.domain.exception.InternalCallException policy 가 받지 않았을 때
     */
    BulkResult bulk(String extractedBy, String promptVersion, LocalDateTime extractedAt, List<PolicyItem> items);
}
