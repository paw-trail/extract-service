package com.pawtrail.extract.infrastructure.provider.internal.dto;

import com.pawtrail.extract.domain.enums.ExtractionMethod;
import com.pawtrail.extract.domain.enums.SourceType;
import com.pawtrail.extract.domain.model.ConditionFields;
import com.pawtrail.extract.domain.model.Evidence;
import com.pawtrail.extract.domain.model.IntraConflict;
import com.pawtrail.extract.domain.model.PolicyItem;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * policy POST /internal/policies/bulk 의 요청입니다. 모양은 policy 의 BulkUpsertRequest 와 같습니다.
 *
 * 조건 칸은 ConditionFields 를 그대로 싣습니다. 칸 이름과 타입이 policy 의 PolicyFieldsRequest 와 같게
 * 만들어 두었고, 비어 있는 칸은 null 로 나가 "정보 없음" 이 됩니다.
 * 근거도 Evidence 를 그대로 싣습니다 — policy 의 EvidenceRequest 와 다섯 칸이 같습니다.
 * 근거 줄의 추출 방식(RULE · LLM)은 policy v0.1.2 부터 받는 칸이며 빠지면 400 입니다.
 * 소스 내 충돌만 모양이 달라 여기서 바꿉니다. policy 는 두 값을 field · text 두 키의 Map 으로 받습니다.
 */
public record PolicyBulkRequest(
        String extractedBy,
        String promptVersion,
        LocalDateTime extractedAt,
        List<Item> items
) {

    public static PolicyBulkRequest of(String extractedBy, String promptVersion,
                                       LocalDateTime extractedAt, List<PolicyItem> items) {
        return new PolicyBulkRequest(extractedBy, promptVersion, extractedAt,
                items.stream().map(Item::of).toList());
    }

    public record Item(
            UUID placeId,
            SourceType source,
            ConditionFields fields,
            List<Evidence> evidence,
            List<Conflict> conflicts,
            ExtractionMethod extractionMethod
    ) {

        static Item of(PolicyItem item) {
            return new Item(item.placeId(), item.source(), item.fields(), item.evidence(),
                    item.conflicts().stream().map(Conflict::of).toList(), item.method());
        }
    }

    /**
     * @param sourceValues 두 키 — field 는 정형 칸이 말한 것, text 는 본문이 말한 것
     */
    public record Conflict(String fieldName, Map<String, String> sourceValues) {

        static Conflict of(IntraConflict conflict) {
            return new Conflict(conflict.fieldName(),
                    Map.of("field", conflict.fieldText(), "text", conflict.bodyText()));
        }
    }
}
