package com.pawtrail.extract.eval;

import com.pawtrail.extract.domain.enums.SizeRule;
import com.pawtrail.extract.domain.exception.LlmDocumentException;
import com.pawtrail.extract.domain.model.ConditionFields;
import com.pawtrail.extract.domain.model.Evidence;
import com.pawtrail.extract.domain.model.FieldNames;
import com.pawtrail.extract.domain.model.LlmReading;
import com.pawtrail.extract.domain.model.Segment;
import com.pawtrail.extract.eval.EvalScorer.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvalScoreTest {

    private static final List<Segment> SEGMENTS = List.of(
            new Segment(1, "acmpyPsblCpam", null, "전 견종 동반 가능"),
            new Segment(2, "acmpyNeedMtr", 1, "목줄 착용"),
            new Segment(3, "etcAcmpyInfo", 1, "- 배변봉투 지참 및 배변처리 필수"));

    @Test
    @DisplayName("칸마다 경우를 세고, 값이 맞은 칸은 근거 적중을, 갈린 칸은 목록에 싣는다")
    void 모으기() {
        EvalSample sample = sample(Map.of("sizeRule", "ALL", "leashRequired", true, "requiredItems", List.of("배변봉투")),
                Map.of("sizeRule", List.of(1), "leashRequired", List.of(2), "requiredItems", List.of(3)));
        LlmReading reading = new LlmReading(
                ConditionFields.builder().sizeRule(SizeRule.ALL).leashRequired(true).vaccineProof(true).build(),
                List.of(Evidence.ofLlm("sizeRule", "acmpyPsblCpam", null, "전 견종 동반 가능"),
                        Evidence.ofLlm("leashRequired", "etcAcmpyInfo", 1, "- 배변봉투 지참 및 배변처리 필수"),
                        Evidence.ofLlm("vaccineProof", "acmpyPsblCpam", null, "전 견종 동반 가능")),
                0, 0, 0);

        EvalScore score = new EvalScore();
        score.add(sample, SEGMENTS, reading);

        assertThat(score.count("sizeRule", Verdict.MATCH)).isEqualTo(1);
        assertThat(score.count("requiredItems", Verdict.MISSED)).isEqualTo(1);
        assertThat(score.count("vaccineProof", Verdict.FABRICATED)).isEqualTo(1);
        assertThat(score.total(Verdict.BOTH_EMPTY)).isEqualTo(FieldNames.ALL.size() - 4);
        assertThat(score.evidenceChecked()).isEqualTo(2);
        assertThat(score.evidenceHit()).isEqualTo(1);
        assertThat(score.disagreements()).extracting(EvalScore.Disagreement::field)
                .containsExactly("requiredItems", "vaccineProof");
    }

    @Test
    @DisplayName("문서 탓 실패는 칸별 표에 넣지 않고 따로 센다")
    void 실패() {
        EvalScore score = new EvalScore();
        score.addFailure(sample(Map.of(), Map.of()), SEGMENTS,
                new LlmDocumentException(LlmDocumentException.Reason.TRUNCATED, "잘림"));

        assertThat(score.failures()).hasSize(1);
        assertThat(score.scored()).isZero();
        assertThat(score.total(Verdict.BOTH_EMPTY)).isZero();
    }

    @Test
    @DisplayName("근거를 문서 전체의 조각 번호로 되돌린다")
    void 번호_되돌리기() {
        Map<String, List<Integer>> numbers = EvalScore.numbers(List.of(
                Evidence.ofLlm("leashRequired", "acmpyNeedMtr", 1, "목줄 착용"),
                Evidence.ofLlm("requiredItems", "etcAcmpyInfo", 1, "- 배변봉투 지참 및 배변처리 필수")), SEGMENTS);

        assertThat(numbers).containsEntry("leashRequired", List.of(2)).containsEntry("requiredItems", List.of(3));
    }

    private static EvalSample sample(Map<String, Object> values, Map<String, List<Integer>> evidence) {
        Map<String, Object> fields = new HashMap<>();
        for (String name : FieldNames.ALL) {
            fields.put(name, values.get(name));
        }
        return new EvalSample(1, "test-id", "PET_TOUR", "무작위", fields, evidence);
    }
}
