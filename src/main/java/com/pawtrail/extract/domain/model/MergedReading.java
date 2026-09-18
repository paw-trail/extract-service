package com.pawtrail.extract.domain.model;

import com.pawtrail.extract.domain.enums.ExtractionMethod;

import java.util.List;

/**
 * 한 원문에서 규칙과 모델이 읽은 것을 합친 결과입니다.
 *
 * <pre>
 * fields      합친 조건 20칸 — 두 쪽이 갈린 칸은 비어 있음
 * evidence    남은 값마다의 근거 — 칸 순서 → 규칙 근거 → 모델 근거
 * conflicts   두 쪽이 갈린 자리
 * method      무엇으로 읽었는지
 * </pre>
 */
public record MergedReading(
        ConditionFields fields,
        List<Evidence> evidence,
        List<IntraConflict> conflicts,
        ExtractionMethod method
) {

    public MergedReading {
        evidence = List.copyOf(evidence);
        conflicts = List.copyOf(conflicts);
    }
}
