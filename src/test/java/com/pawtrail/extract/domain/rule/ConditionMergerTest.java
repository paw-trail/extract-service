package com.pawtrail.extract.domain.rule;

import com.pawtrail.extract.domain.enums.BreedRule;
import com.pawtrail.extract.domain.enums.ExtraFeeUnit;
import com.pawtrail.extract.domain.enums.ExtractionMethod;
import com.pawtrail.extract.domain.enums.Scope;
import com.pawtrail.extract.domain.enums.SizeRule;
import com.pawtrail.extract.domain.model.ConditionFields;
import com.pawtrail.extract.domain.model.Evidence;
import com.pawtrail.extract.domain.model.FieldNames;
import com.pawtrail.extract.domain.model.IntraConflict;
import com.pawtrail.extract.domain.model.LlmReading;
import com.pawtrail.extract.domain.model.MergedReading;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 규칙과 모델이 읽은 것을 칸마다 합치는 규칙을 봅니다.
 *
 * 가부 묶음 예시 셋은 착수 때 실데이터로 정한 것입니다 (고캠핑 불가능 대 소형견만 출입 허용 ·
 * 문화정보원 실내 Y 대 야외만 · 공사 전구역 대 실내는 동반 불가).
 */
class ConditionMergerTest {

    @Test
    @DisplayName("한쪽만 값이 있으면 그 값과 그 근거를 쓴다")
    void 한쪽만_값() {
        ConditionFields rule = ConditionFields.builder().outdoorAllowed(true).build();
        List<Evidence> ruleEvidence = List.of(Evidence.ofRule(FieldNames.OUTDOOR_ALLOWED, "animalCmgCl", "가능"));
        LlmReading llm = reading(ConditionFields.builder().leashRequired(true).build(),
                llmEvidence(FieldNames.LEASH_REQUIRED, "intro", 2, "리드줄 필수"));

        MergedReading merged = ConditionMerger.merge(rule, ruleEvidence, llm);

        assertThat(merged.fields().outdoorAllowed()).isTrue();
        assertThat(merged.fields().leashRequired()).isTrue();
        assertThat(merged.conflicts()).isEmpty();
        assertThat(merged.evidence()).extracting(Evidence::fieldName)
                .containsExactly(FieldNames.OUTDOOR_ALLOWED, FieldNames.LEASH_REQUIRED);
        assertThat(merged.method()).isEqualTo(ExtractionMethod.MIXED);
    }

    @Test
    @DisplayName("둘 다 같은 값이면 규칙 값을 쓰고 근거는 둘 다 남긴다")
    void 같은_값() {
        ConditionFields rule = ConditionFields.builder().sizeRule(SizeRule.SMALL_ONLY).build();
        List<Evidence> ruleEvidence = List.of(Evidence.ofRule(FieldNames.SIZE_RULE, "animalCmgCl", "가능(소형견)"));
        LlmReading llm = reading(ConditionFields.builder().sizeRule(SizeRule.SMALL_ONLY).build(),
                llmEvidence(FieldNames.SIZE_RULE, "intro", 1, "소형견만 동반 가능"));

        MergedReading merged = ConditionMerger.merge(rule, ruleEvidence, llm);

        assertThat(merged.fields().sizeRule()).isEqualTo(SizeRule.SMALL_ONLY);
        assertThat(merged.conflicts()).isEmpty();
        assertThat(merged.evidence()).extracting(Evidence::segmentText)
                .containsExactly("가능(소형견)", "소형견만 동반 가능");
    }

    @Test
    @DisplayName("같은 칸이 다르면 비우고 원문 그대로 충돌로 남긴다")
    void 다른_값() {
        ConditionFields rule = ConditionFields.builder().maxWeightKg(new BigDecimal("10")).build();
        List<Evidence> ruleEvidence = List.of(Evidence.ofRule(FieldNames.MAX_WEIGHT_KG, "입장 가능 동물 크기", "10kg 미만"));
        LlmReading llm = reading(ConditionFields.builder().maxWeightKg(new BigDecimal("15")).build(),
                llmEvidence(FieldNames.MAX_WEIGHT_KG, "반려동물 제한사항", 0, "15kg 이하 동반 가능"));

        MergedReading merged = ConditionMerger.merge(rule, ruleEvidence, llm);

        assertThat(merged.fields().maxWeightKg()).isNull();
        assertThat(merged.evidence()).isEmpty();
        assertThat(merged.conflicts()).containsExactly(
                new IntraConflict(FieldNames.MAX_WEIGHT_KG, "10kg 미만", "15kg 이하 동반 가능"));
    }

    @Test
    @DisplayName("숫자 표기와 목록 순서만 다르면 같은 값으로 본다")
    void 표기만_다름() {
        ConditionFields rule = ConditionFields.builder()
                .maxWeightKg(new BigDecimal("10"))
                .requiredItems(List.of("목줄", "배변봉투"))
                .build();
        LlmReading llm = reading(ConditionFields.builder()
                        .maxWeightKg(new BigDecimal("10.00"))
                        .requiredItems(List.of("배변봉투 ", "목줄"))
                        .build(),
                llmEvidence(FieldNames.MAX_WEIGHT_KG, "t", 0, "10kg"),
                llmEvidence(FieldNames.REQUIRED_ITEMS, "t", 1, "목줄, 배변봉투"));

        MergedReading merged = ConditionMerger.merge(rule, List.of(), llm);

        assertThat(merged.conflicts()).isEmpty();
        assertThat(merged.fields().maxWeightKg()).isEqualByComparingTo("10");
        assertThat(merged.fields().requiredItems()).containsExactly("목줄", "배변봉투");
    }

    @Test
    @DisplayName("고캠핑 불가능 대 소형견만 출입 허용 — 가부 셋을 비우고 범위에 한 줄 · 크기는 그대로")
    void 가부가_정면으로_갈림() {
        ConditionFields rule = ConditionFields.builder()
                .scope(Scope.NONE).indoorAllowed(false).outdoorAllowed(false).build();
        List<Evidence> ruleEvidence = List.of(
                Evidence.ofRule(FieldNames.SCOPE, "animalCmgCl", "불가능"),
                Evidence.ofRule(FieldNames.INDOOR_ALLOWED, "animalCmgCl", "불가능"),
                Evidence.ofRule(FieldNames.OUTDOOR_ALLOWED, "animalCmgCl", "불가능"));
        LlmReading llm = reading(ConditionFields.builder()
                        .outdoorAllowed(true).sizeRule(SizeRule.SMALL_ONLY).build(),
                llmEvidence(FieldNames.OUTDOOR_ALLOWED, "intro", 3, "소형견만 출입 허용"),
                llmEvidence(FieldNames.SIZE_RULE, "intro", 3, "소형견만 출입 허용"));

        MergedReading merged = ConditionMerger.merge(rule, ruleEvidence, llm);

        assertThat(merged.fields().scope()).isNull();
        assertThat(merged.fields().indoorAllowed()).isNull();
        assertThat(merged.fields().outdoorAllowed()).isNull();
        assertThat(merged.fields().sizeRule()).isEqualTo(SizeRule.SMALL_ONLY);
        // 칸마다 따로 보면 범위 · 실외 두 줄로 쪼개지고 실내 false 가 새어 나옴
        assertThat(merged.conflicts()).containsExactly(
                new IntraConflict(FieldNames.SCOPE, "불가능", "소형견만 출입 허용"));
        assertThat(merged.evidence()).extracting(Evidence::fieldName).containsExactly(FieldNames.SIZE_RULE);
    }

    @Test
    @DisplayName("고캠핑 불가능 대 모델이 크기만 읽음 — 가부 셋이 조용해도 허용 쪽 칸이면 갈린 것으로 본다")
    void 허용_쪽_칸만_읽어도_갈림() {
        ConditionFields rule = ConditionFields.builder()
                .scope(Scope.NONE).indoorAllowed(false).outdoorAllowed(false).build();
        List<Evidence> ruleEvidence = List.of(
                Evidence.ofRule(FieldNames.SCOPE, "animalCmgCl", "불가능"),
                Evidence.ofRule(FieldNames.INDOOR_ALLOWED, "animalCmgCl", "불가능"),
                Evidence.ofRule(FieldNames.OUTDOOR_ALLOWED, "animalCmgCl", "불가능"));
        LlmReading llm = reading(ConditionFields.builder().sizeRule(SizeRule.SMALL_ONLY).build(),
                llmEvidence(FieldNames.SIZE_RULE, "intro", 3, "소형견만 출입 허용"));

        MergedReading merged = ConditionMerger.merge(rule, ruleEvidence, llm);

        // 세 칸만 보면 모델이 조용한 쪽이라 불가와 크기가 한 행에 함께 나가고 판정은 불가가 됨
        assertThat(merged.fields().scope()).isNull();
        assertThat(merged.fields().indoorAllowed()).isNull();
        assertThat(merged.fields().outdoorAllowed()).isNull();
        assertThat(merged.fields().sizeRule()).isEqualTo(SizeRule.SMALL_ONLY);
        assertThat(merged.conflicts()).containsExactly(
                new IntraConflict(FieldNames.SCOPE, "불가능", "소형견만 출입 허용"));
    }

    @Test
    @DisplayName("목줄 같은 동반 방법 칸만 읽어도 허용 쪽이고 갈린 글은 그 칸들의 근거에서 모은다")
    void 동반_방법_칸도_허용_쪽() {
        ConditionFields rule = ConditionFields.builder()
                .scope(Scope.NONE).indoorAllowed(false).outdoorAllowed(false).build();
        List<Evidence> ruleEvidence = List.of(Evidence.ofRule(FieldNames.SCOPE, "animalCmgCl", "불가능"));
        LlmReading llm = reading(ConditionFields.builder()
                        .leashRequired(true).requiredItems(List.of("배변봉투")).build(),
                llmEvidence(FieldNames.LEASH_REQUIRED, "intro", 2, "반려견은 목줄 착용 후 입장"),
                llmEvidence(FieldNames.REQUIRED_ITEMS, "intro", 4, "배변봉투 지참"));

        MergedReading merged = ConditionMerger.merge(rule, ruleEvidence, llm);

        // 가부 세 칸의 근거만 모으면 모델 쪽 글이 비어 policy 가 400 으로 막음
        assertThat(merged.conflicts()).containsExactly(
                new IntraConflict(FieldNames.SCOPE, "불가능", "반려견은 목줄 착용 후 입장 / 배변봉투 지참"));
        assertThat(merged.fields().leashRequired()).isTrue();
        assertThat(merged.fields().requiredItems()).containsExactly("배변봉투");
    }

    @Test
    @DisplayName("안내견 한정 · 사전 문의 · 맹견 불가 · 짝 칸 · 빈 목록 · 전용 아님은 허용 쪽으로 보지 않는다")
    void 허용_쪽이_아닌_칸() {
        ConditionFields rule = ConditionFields.builder()
                .scope(Scope.NONE).indoorAllowed(false).outdoorAllowed(false).build();
        List<Evidence> ruleEvidence = List.of(Evidence.ofRule(FieldNames.SCOPE, "animalCmgCl", "불가능"));
        LlmReading llm = reading(ConditionFields.builder()
                        .guideDogOnly(true).advanceInquiry(true).breedRule(BreedRule.DANGEROUS_BANNED)
                        .weightInclusive(true).extraFeeUnit(ExtraFeeUnit.PER_DOG)
                        .requiredItems(List.of()).petOnly(false).build(),
                llmEvidence(FieldNames.GUIDE_DOG_ONLY, "intro", 1, "안내견만 출입 가능"));

        MergedReading merged = ConditionMerger.merge(rule, ruleEvidence, llm);

        // 안내견 한정은 불가와 같은 쪽이고 사전 문의는 동반 여부를 묻는 데도 쓰임
        assertThat(merged.conflicts()).isEmpty();
        assertThat(merged.fields().scope()).isEqualTo(Scope.NONE);
        assertThat(merged.fields().guideDogOnly()).isTrue();
    }

    @Test
    @DisplayName("반대 방향 — 규칙이 허용 쪽 칸만 말하고 모델이 불가면 갈린 것으로 본다")
    void 반대_방향도_갈림() {
        ConditionFields rule = ConditionFields.builder().sizeRule(SizeRule.SMALL_ONLY).build();
        List<Evidence> ruleEvidence = List.of(Evidence.ofRule(FieldNames.SIZE_RULE, "입장 가능 동물 크기", "소형"));
        LlmReading llm = reading(ConditionFields.builder().scope(Scope.NONE).build(),
                llmEvidence(FieldNames.SCOPE, "반려동물 제한사항", 0, "반려동물 동반 불가"));

        MergedReading merged = ConditionMerger.merge(rule, ruleEvidence, llm);

        assertThat(merged.fields().scope()).isNull();
        assertThat(merged.fields().sizeRule()).isEqualTo(SizeRule.SMALL_ONLY);
        assertThat(merged.conflicts()).containsExactly(
                new IntraConflict(FieldNames.SCOPE, "소형", "반려동물 동반 불가"));
    }

    @Test
    @DisplayName("문화정보원 실내 Y 대 야외만 동반 가능 — 실내만 비우고 실외는 그대로")
    void 실내만_갈림() {
        ConditionFields rule = ConditionFields.builder().indoorAllowed(true).outdoorAllowed(true).build();
        List<Evidence> ruleEvidence = List.of(
                Evidence.ofRule(FieldNames.INDOOR_ALLOWED, "장소(실내) 여부", "장소(실내) 여부 Y"),
                Evidence.ofRule(FieldNames.OUTDOOR_ALLOWED, "장소(실외)여부", "장소(실외)여부 Y"));
        LlmReading llm = reading(ConditionFields.builder().indoorAllowed(false).outdoorAllowed(true).build(),
                llmEvidence(FieldNames.INDOOR_ALLOWED, "반려동물 제한사항", 0, "야외만 동반 가능"),
                llmEvidence(FieldNames.OUTDOOR_ALLOWED, "반려동물 제한사항", 0, "야외만 동반 가능"));

        MergedReading merged = ConditionMerger.merge(rule, ruleEvidence, llm);

        // 둘 다 동반된다고 말하므로 묶음 충돌이 아님 — 칸마다 봄
        assertThat(merged.fields().indoorAllowed()).isNull();
        assertThat(merged.fields().outdoorAllowed()).isTrue();
        assertThat(merged.conflicts()).containsExactly(
                new IntraConflict(FieldNames.INDOOR_ALLOWED, "장소(실내) 여부 Y", "야외만 동반 가능"));
    }

    @Test
    @DisplayName("공사 전구역 대 모델이 일부 구역으로 읽음 — 범위만 비움")
    void 범위만_갈림() {
        ConditionFields rule = ConditionFields.builder().scope(Scope.ALL_AREA).build();
        List<Evidence> ruleEvidence = List.of(Evidence.ofRule(FieldNames.SCOPE, "acmpyTypeCd", "전구역 동반가능"));
        LlmReading llm = reading(ConditionFields.builder()
                        .scope(Scope.PARTIAL).excludedZones(List.of("실내")).build(),
                llmEvidence(FieldNames.SCOPE, "etcAcmpyInfo", 1, "실내는 동반 불가"),
                llmEvidence(FieldNames.EXCLUDED_ZONES, "etcAcmpyInfo", 1, "실내는 동반 불가"));

        MergedReading merged = ConditionMerger.merge(rule, ruleEvidence, llm);

        assertThat(merged.fields().scope()).isNull();
        assertThat(merged.fields().excludedZones()).containsExactly("실내");
        assertThat(merged.conflicts()).containsExactly(
                new IntraConflict(FieldNames.SCOPE, "전구역 동반가능", "실내는 동반 불가"));
    }

    @Test
    @DisplayName("모델에 넘길 문장이 없던 원문은 규칙 결과 그대로이고 RULE 이다")
    void 규칙만() {
        ConditionFields rule = ConditionFields.builder().scope(Scope.PARTIAL).build();
        List<Evidence> ruleEvidence = List.of(Evidence.ofRule(FieldNames.SCOPE, "acmpyTypeCd", "일부구역 동반가능"));

        MergedReading merged = ConditionMerger.merge(rule, ruleEvidence, LlmReading.empty());

        assertThat(merged.fields()).isEqualTo(rule);
        assertThat(merged.evidence()).isEqualTo(ruleEvidence);
        assertThat(merged.method()).isEqualTo(ExtractionMethod.RULE);
    }

    @Test
    @DisplayName("모델만 읽었으면 LLM · 둘 다 비었으면 RULE 이다")
    void 추출_방식() {
        LlmReading llm = reading(ConditionFields.builder().leashRequired(true).build(),
                llmEvidence(FieldNames.LEASH_REQUIRED, "t", 0, "목줄 착용"));

        assertThat(ConditionMerger.merge(ConditionFields.empty(), List.of(), llm).method())
                .isEqualTo(ExtractionMethod.LLM);
        assertThat(ConditionMerger.merge(ConditionFields.empty(), List.of(), LlmReading.empty()).method())
                .isEqualTo(ExtractionMethod.RULE);
    }

    @Test
    @DisplayName("두 쪽이 갈려 칸을 다 비워도 둘 다 읽었으므로 MIXED 이다")
    void 전부_갈려도_MIXED() {
        ConditionFields rule = ConditionFields.builder().maxCount((short) 1).build();
        LlmReading llm = reading(ConditionFields.builder().maxCount((short) 2).build(),
                llmEvidence(FieldNames.MAX_COUNT, "t", 0, "2마리까지"));

        MergedReading merged = ConditionMerger.merge(rule,
                List.of(Evidence.ofRule(FieldNames.MAX_COUNT, "k", "1마리")), llm);

        assertThat(merged.fields().isEmpty()).isTrue();
        assertThat(merged.method()).isEqualTo(ExtractionMethod.MIXED);
    }

    private static LlmReading reading(ConditionFields fields, Evidence... evidence) {
        return new LlmReading(fields, List.of(evidence), 0, 0, 0);
    }

    private static Evidence llmEvidence(String field, String origin, int index, String text) {
        return Evidence.ofLlm(field, origin, index, text);
    }
}
