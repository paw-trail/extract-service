package com.pawtrail.extract.domain.model;

import com.pawtrail.extract.domain.enums.ExtractionMethod;
import com.pawtrail.extract.domain.enums.SourceType;

import java.util.List;
import java.util.UUID;

/**
 * policy bulk 요청의 항목 하나 — 한 장소 · 한 소스의 조건입니다.
 *
 * <pre>
 * placeId     장소
 * source      소스 — 공공 세 가지뿐
 * fields      조건 20칸 — 조건이 없는 원문은 20칸이 빈 채로 보냄
 * evidence    칸마다의 근거
 * conflicts   소스 내 충돌
 * method      무엇으로 읽었는지
 * </pre>
 *
 * 20칸이 빈 행도 보내는 이유는 policy 에서 그것이 "뽑았으나 조건을 못 찾음" 을 뜻하기 때문입니다.
 * 보내지 않으면 원문이 조건을 지웠을 때 예전 조건이 그대로 남습니다.
 */
public record PolicyItem(
        UUID placeId,
        SourceType source,
        ConditionFields fields,
        List<Evidence> evidence,
        List<IntraConflict> conflicts,
        ExtractionMethod method
) {

    public PolicyItem {
        evidence = List.copyOf(evidence);
        conflicts = List.copyOf(conflicts);
    }

    public static PolicyItem of(UUID placeId, SourceType source, MergedReading reading) {
        return new PolicyItem(placeId, source, reading.fields(), reading.evidence(),
                reading.conflicts(), reading.method());
    }
}
