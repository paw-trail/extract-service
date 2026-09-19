package com.pawtrail.extract.domain.model;

import com.pawtrail.extract.domain.enums.RuleOutcome;

import java.util.List;

/**
 * 원문 한 건에 대한 규칙 추출의 결과입니다.
 *
 * <pre>
 * outcome    갈래 — SEND · EMPTY · SKIP_DONE · FAILED
 * fields     규칙이 채운 칸
 * evidence   그 칸들의 근거
 * texts      LLM 에 넘길 원문 칸
 * reason     SKIP_DONE · FAILED 일 때 그 까닭 — 로그에 남김
 * </pre>
 *
 * SEND 와 EMPTY 는 규칙이 직접 가리지 않고 결과를 모은 뒤 한 번에 가립니다.
 * 규칙 칸도 넘길 원문도 없을 때만 EMPTY 이므로, 규칙마다 따로 판단하면
 * 한쪽을 빠뜨렸을 때 조건이 있는 원문이 빈 행으로 나갑니다.
 */
public record RuleResult(
        RuleOutcome outcome,
        ConditionFields fields,
        List<Evidence> evidence,
        List<SourceText> texts,
        String reason
) {

    public RuleResult {
        evidence = List.copyOf(evidence);
        texts = List.copyOf(texts);
    }

    /**
     * 규칙 칸과 넘길 원문으로 SEND 와 EMPTY 를 가립니다.
     */
    public static RuleResult of(ConditionFields fields, List<Evidence> evidence, List<SourceText> texts) {
        RuleOutcome outcome = fields.isEmpty() && texts.isEmpty() ? RuleOutcome.EMPTY : RuleOutcome.SEND;
        return new RuleResult(outcome, fields, evidence, texts, null);
    }

    public static RuleResult skipDone(String reason) {
        return new RuleResult(RuleOutcome.SKIP_DONE, ConditionFields.empty(), List.of(), List.of(), reason);
    }

    public static RuleResult failed(String reason) {
        return new RuleResult(RuleOutcome.FAILED, ConditionFields.empty(), List.of(), List.of(), reason);
    }
}
