package com.pawtrail.extract.domain.rule;

import com.pawtrail.extract.domain.enums.ExtractionMethod;
import com.pawtrail.extract.domain.enums.SourceType;
import com.pawtrail.extract.domain.model.ConditionFields;
import com.pawtrail.extract.domain.model.Evidence;
import com.pawtrail.extract.domain.model.FieldNames;
import com.pawtrail.extract.domain.model.IntraConflict;
import com.pawtrail.extract.domain.model.PolicyItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * policy 의 요청 검증과 같은 규칙인지 봅니다. 여기서 놓치면 청크 전체가 400 으로 돌아옵니다.
 */
class PolicyItemCheckTest {

    @Test
    @DisplayName("정상 항목은 걸리는 것이 없다")
    void 정상() {
        PolicyItem item = item(ConditionFields.builder()
                        .maxWeightKg(new BigDecimal("999.99")).maxCount((short) 1).extraFeeAmount(0).build(),
                List.of(Evidence.ofLlm(FieldNames.MAX_WEIGHT_KG, "etcAcmpyInfo", 0, "10kg 이하")),
                List.of(new IntraConflict(FieldNames.SCOPE, "불가능", "소형견만 출입 허용")));

        assertThat(PolicyItemCheck.check(item)).isEmpty();
    }

    @Test
    @DisplayName("20칸이 빈 행도 보낼 수 있다")
    void 빈_행() {
        assertThat(PolicyItemCheck.check(item(ConditionFields.empty(), List.of(), List.of()))).isEmpty();
    }

    @Test
    @DisplayName("체중 상한은 0 보다 크고 정수 3자리 · 소수 2자리까지다")
    void 체중_상한() {
        assertThat(PolicyItemCheck.check(weight("0"))).hasSize(1);
        assertThat(PolicyItemCheck.check(weight("-3"))).hasSize(1);
        assertThat(PolicyItemCheck.check(weight("1000"))).hasSize(1);
        assertThat(PolicyItemCheck.check(weight("5.125"))).hasSize(1);
        assertThat(PolicyItemCheck.check(weight("5.00"))).isEmpty();
    }

    @Test
    @DisplayName("마릿수는 1 이상 · 추가 요금은 0 이상이다")
    void 마릿수와_요금() {
        assertThat(PolicyItemCheck.check(item(ConditionFields.builder().maxCount((short) 0).build(),
                List.of(), List.of()))).hasSize(1);
        assertThat(PolicyItemCheck.check(item(ConditionFields.builder().extraFeeAmount(-1).build(),
                List.of(), List.of()))).hasSize(1);
    }

    @Test
    @DisplayName("근거는 20칸 이름 · 40자 이하 원문 키 · 비지 않은 문구여야 한다")
    void 근거() {
        List<Evidence> bad = List.of(
                Evidence.ofLlm("unknownField", "k", 0, "t"),
                Evidence.ofLlm(FieldNames.SCOPE, "k".repeat(41), 0, "t"),
                Evidence.ofLlm(FieldNames.SCOPE, "k", 0, " "));

        assertThat(PolicyItemCheck.check(item(ConditionFields.empty(), bad, List.of()))).hasSize(3);
    }

    @Test
    @DisplayName("근거의 추출 방식은 RULE · LLM 이어야 한다")
    void 근거의_추출_방식() {
        // policy 가 근거 줄마다 받는 값이라 빠지거나 출처 행의 값(MIXED)이 오면 청크 전체가 400
        List<Evidence> bad = List.of(
                new Evidence(FieldNames.SCOPE, "k", 0, "t", null),
                new Evidence(FieldNames.SCOPE, "k", 0, "t", ExtractionMethod.MIXED));

        assertThat(PolicyItemCheck.check(item(ConditionFields.empty(), bad, List.of()))).hasSize(2);
    }

    @Test
    @DisplayName("충돌은 20칸 이름이고 두 값이 모두 있어야 한다")
    void 충돌() {
        List<IntraConflict> bad = List.of(
                new IntraConflict("unknownField", "a", "b"),
                new IntraConflict(FieldNames.SCOPE, "불가능", ""));

        assertThat(PolicyItemCheck.check(item(ConditionFields.empty(), List.of(), bad))).hasSize(2);
    }

    @Test
    @DisplayName("모델 이름은 50자 · 프롬프트 판은 20자까지다")
    void 실행_값() {
        assertThat(PolicyItemCheck.checkBatch("gpt-5.6-luna", "v2")).isEmpty();
        assertThat(PolicyItemCheck.checkBatch("m".repeat(51), "v".repeat(21))).hasSize(2);
    }

    private static PolicyItem weight(String value) {
        return item(ConditionFields.builder().maxWeightKg(new BigDecimal(value)).build(), List.of(), List.of());
    }

    private static PolicyItem item(ConditionFields fields, List<Evidence> evidence, List<IntraConflict> conflicts) {
        return new PolicyItem(UUID.randomUUID(), SourceType.PET_TOUR, fields, evidence, conflicts, ExtractionMethod.MIXED);
    }
}
