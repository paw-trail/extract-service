package com.pawtrail.extract.eval;

import com.pawtrail.extract.application.support.LlmReuse;
import com.pawtrail.extract.domain.enums.SizeRule;
import com.pawtrail.extract.domain.model.ConditionFields;
import com.pawtrail.extract.domain.model.Evidence;
import com.pawtrail.extract.domain.model.FieldNames;
import com.pawtrail.extract.domain.model.LlmReading;
import com.pawtrail.extract.domain.model.Segment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 보고서의 무작위 표본 절이 같았던 값이 없는 까닭을 맞게 적는지 봅니다.
 */
class EvalReportWriterTest {

    private static final List<Segment> SEGMENTS = List.of(new Segment(1, "acmpyPsblCpam", null, "소형견만 동반 가능"));

    @Test
    @DisplayName("정답과 모델이 모든 칸을 비웠으면 그렇다고 적는다")
    void 둘_다_비움() {
        EvalScore score = new EvalScore();
        score.add(sample(Map.of(), Map.of()), SEGMENTS, new LlmReading(ConditionFields.empty(), List.of(), 0, 0, 0));

        String markdown = EvalReportWriter.markdown("m", "v2", "answers", score, new LlmReuse(), Duration.ZERO);

        assertThat(markdown).contains("(정답과 모델 모두 모든 칸을 비움)").doesNotContain("(같았던 값이 없음");
    }

    @Test
    @DisplayName("값이 있는데 전부 갈렸으면 둘 다 비웠다고 적지 않는다")
    void 전부_갈림() {
        EvalScore score = new EvalScore();
        LlmReading reading = new LlmReading(
                ConditionFields.builder().sizeRule(SizeRule.ALL).build(),
                List.of(Evidence.ofLlm("sizeRule", "acmpyPsblCpam", null, "소형견만 동반 가능")),
                0, 0, 0);
        score.add(sample(Map.of("sizeRule", "SMALL_ONLY"), Map.of("sizeRule", List.of(1))), SEGMENTS, reading);

        String markdown = EvalReportWriter.markdown("m", "v2", "answers", score, new LlmReuse(), Duration.ZERO);

        assertThat(markdown).contains("(같았던 값이 없음 — 이 표본의 칸은 위 「갈린 칸」 에 있음)")
                .doesNotContain("(정답과 모델 모두 모든 칸을 비움)");
    }

    private static EvalSample sample(Map<String, Object> values, Map<String, List<Integer>> evidence) {
        Map<String, Object> fields = new HashMap<>();
        for (String name : FieldNames.ALL) {
            fields.put(name, values.get(name));
        }
        return new EvalSample(1, "test-id", "PET_TOUR", "무작위", fields, evidence);
    }
}
