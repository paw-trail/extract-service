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
 * 두 읽기를 안전 쪽으로 합치는 규칙을 칸 종류마다 봅니다.
 *
 * 핵심은 "허용을 넓히는 값은 두 읽기가 같이 말할 때만 믿는다" 와 "근거는 고른 값을 낸 읽기 것" 입니다.
 */
class ReadingMergerTest {

    @Test
    @DisplayName("단계가 있는 칸은 둘 다 값이면 좁은 쪽을 고르고 그 읽기의 근거를 붙인다")
    void 좁은_쪽() {
        LlmReading first = reading(ConditionFields.builder().sizeRule(SizeRule.ALL).breedRule(BreedRule.DANGEROUS_BANNED).build(),
                line(FieldNames.SIZE_RULE, "전 견종 가능"), line(FieldNames.BREED_RULE, "맹견 출입 금지"));
        LlmReading second = reading(ConditionFields.builder().sizeRule(SizeRule.SMALL_ONLY).breedRule(BreedRule.DANGEROUS_MUZZLE).build(),
                line(FieldNames.SIZE_RULE, "소형견만"), line(FieldNames.BREED_RULE, "맹견 입마개"));

        LlmReading merged = ReadingMerger.merge(first, second);

        assertThat(merged.fields().sizeRule()).isEqualTo(SizeRule.SMALL_ONLY);
        assertThat(merged.fields().breedRule()).isEqualTo(BreedRule.DANGEROUS_BANNED);
        // 근거는 칸 순서(크기 → 견종)로 · 고른 값을 낸 읽기 것만
        assertThat(merged.evidence()).extracting(Evidence::segmentText).containsExactly("소형견만", "맹견 출입 금지");
    }

    @Test
    @DisplayName("한쪽만 말한 가장 넓은 값은 비우고, 좁은 값은 그대로 둔다")
    void 한쪽만() {
        LlmReading first = reading(ConditionFields.builder().scope(Scope.ALL_AREA).sizeRule(SizeRule.SMALL_ONLY).build(),
                line(FieldNames.SCOPE, "전 구역 가능"), line(FieldNames.SIZE_RULE, "소형견만"));

        LlmReading merged = ReadingMerger.merge(first, reading(ConditionFields.empty()));

        assertThat(merged.fields().scope()).isNull();
        assertThat(merged.fields().sizeRule()).isEqualTo(SizeRule.SMALL_ONLY);
        assertThat(merged.evidence()).extracting(Evidence::fieldName).containsExactly(FieldNames.SIZE_RULE);
    }

    @Test
    @DisplayName("같은 값이면 두 근거를 모두 두고 똑같은 근거는 한 번만 둔다")
    void 같은_값() {
        Evidence same = line(FieldNames.SCOPE, "일부 구역 동반 가능");
        LlmReading first = reading(ConditionFields.builder().scope(Scope.PARTIAL).build(), same);
        LlmReading second = reading(ConditionFields.builder().scope(Scope.PARTIAL).build(),
                same, line(FieldNames.SCOPE, "실내 불가"));

        LlmReading merged = ReadingMerger.merge(first, second);

        assertThat(merged.fields().scope()).isEqualTo(Scope.PARTIAL);
        assertThat(merged.evidence()).extracting(Evidence::segmentText).containsExactly("일부 구역 동반 가능", "실내 불가");
    }

    @Test
    @DisplayName("실내 · 실외는 하나라도 불가면 불가, 둘 다 가능일 때만 가능, 한쪽만 가능이면 비운다")
    void 실내_실외() {
        LlmReading first = reading(ConditionFields.builder().indoorAllowed(true).outdoorAllowed(true).build());
        LlmReading second = reading(ConditionFields.builder().indoorAllowed(false).build());

        LlmReading merged = ReadingMerger.merge(first, second);

        assertThat(merged.fields().indoorAllowed()).isFalse();
        assertThat(merged.fields().outdoorAllowed()).isNull();
        assertThat(ReadingMerger.merge(first, first).fields().outdoorAllowed()).isTrue();
    }

    @Test
    @DisplayName("경고 칸은 하나라도 필요하면 필요, 둘 다 아님일 때만 아님, 한쪽만 아님이면 비운다")
    void 경고_칸() {
        LlmReading first = reading(ConditionFields.builder().leashRequired(false).carrierRequired(false).vaccineProof(false).build());
        LlmReading second = reading(ConditionFields.builder().leashRequired(true).carrierRequired(false).build());

        LlmReading merged = ReadingMerger.merge(first, second);

        assertThat(merged.fields().leashRequired()).isTrue();
        assertThat(merged.fields().carrierRequired()).isFalse();
        assertThat(merged.fields().vaccineProof()).isNull();
    }

    @Test
    @DisplayName("체중 · 마릿수는 작은 쪽이고, 이하 · 미만은 고른 체중을 말한 쪽을 따른다")
    void 상한() {
        LlmReading first = reading(ConditionFields.builder()
                        .maxWeightKg(new BigDecimal("10")).weightInclusive(true).maxCount((short) 3).build(),
                line(FieldNames.MAX_WEIGHT_KG, "10kg 이하"), line(FieldNames.WEIGHT_INCLUSIVE, "10kg 이하"));
        LlmReading second = reading(ConditionFields.builder()
                        .maxWeightKg(new BigDecimal("7")).weightInclusive(false).maxCount((short) 2).build(),
                line(FieldNames.MAX_WEIGHT_KG, "7kg 미만"), line(FieldNames.WEIGHT_INCLUSIVE, "7kg 미만"));

        LlmReading merged = ReadingMerger.merge(first, second);

        assertThat(merged.fields().maxWeightKg()).isEqualByComparingTo("7");
        assertThat(merged.fields().weightInclusive()).isFalse();
        assertThat(merged.fields().maxCount()).isEqualTo((short) 2);
        assertThat(merged.evidence()).extracting(Evidence::segmentText).containsOnly("7kg 미만");
    }

    @Test
    @DisplayName("같은 체중에 이하와 미만이 갈리면 미만을 고른다")
    void 이하_미만() {
        LlmReading first = reading(ConditionFields.builder().maxWeightKg(new BigDecimal("10")).weightInclusive(true).build());
        LlmReading second = reading(ConditionFields.builder().maxWeightKg(new BigDecimal("10.00")).weightInclusive(false).build());

        assertThat(ReadingMerger.merge(first, second).fields().weightInclusive()).isFalse();
    }

    @Test
    @DisplayName("제외 구역 · 준비물은 합치고 앞뒤 공백만 다른 항목은 하나로 둔다")
    void 목록() {
        LlmReading first = reading(ConditionFields.builder().excludedZones(List.of("수영장")).requiredItems(List.of("목줄")).build());
        LlmReading second = reading(ConditionFields.builder().excludedZones(List.of("식당", "수영장 ")).build());

        LlmReading merged = ReadingMerger.merge(first, second);

        assertThat(merged.fields().excludedZones()).containsExactly("수영장", "식당");
        assertThat(merged.fields().requiredItems()).containsExactly("목줄");
    }

    @Test
    @DisplayName("허용 구역 · 추가 요금은 첫 읽기 것을 쓰고, 요금 단위는 금액을 낸 쪽을 따른다")
    void 첫_읽기() {
        LlmReading first = reading(ConditionFields.builder().allowedZonesOnly(List.of("1층")).build());
        LlmReading second = reading(ConditionFields.builder()
                .allowedZonesOnly(List.of("1층", "테라스")).extraFeeAmount(10000).extraFeeUnit(ExtraFeeUnit.PER_DOG).build());

        LlmReading merged = ReadingMerger.merge(first, second);

        assertThat(merged.fields().allowedZonesOnly()).containsExactly("1층");
        assertThat(merged.fields().extraFeeAmount()).isEqualTo(10000);
        assertThat(merged.fields().extraFeeUnit()).isEqualTo(ExtraFeeUnit.PER_DOG);
    }

    @Test
    @DisplayName("파라다이스 캠핑장 — \"가능\" 과 \"가능(소형견)\" 을 합치면 소형견만이 남는다")
    void 형제_원문() {
        MergedReading loose = new MergedReading(ConditionFields.builder().outdoorAllowed(true).build(),
                List.of(Evidence.ofRule(FieldNames.OUTDOOR_ALLOWED, "animalCmgCl", "가능")), List.of(), ExtractionMethod.RULE);
        MergedReading small = new MergedReading(ConditionFields.builder().outdoorAllowed(true).sizeRule(SizeRule.SMALL_ONLY).build(),
                List.of(Evidence.ofRule(FieldNames.OUTDOOR_ALLOWED, "animalCmgCl", "가능(소형견)"),
                        Evidence.ofRule(FieldNames.SIZE_RULE, "animalCmgCl", "가능(소형견)")), List.of(), ExtractionMethod.RULE);

        MergedReading merged = ReadingMerger.merge(small, loose);

        assertThat(merged.fields().outdoorAllowed()).isTrue();
        assertThat(merged.fields().sizeRule()).isEqualTo(SizeRule.SMALL_ONLY);
        assertThat(merged.method()).isEqualTo(ExtractionMethod.RULE);
    }

    @Test
    @DisplayName("형제 원문의 소스 내 충돌은 모두 두고, 충돌이 걸린 칸은 다른 원문에 값이 있어도 비운다")
    void 형제_충돌() {
        MergedReading earlier = new MergedReading(ConditionFields.builder().leashRequired(true).build(),
                List.of(), List.of(new IntraConflict(FieldNames.MAX_WEIGHT_KG, "10kg 미만", "15kg 이하")), ExtractionMethod.MIXED);
        MergedReading later = new MergedReading(ConditionFields.builder().maxWeightKg(new BigDecimal("15")).build(),
                List.of(line(FieldNames.MAX_WEIGHT_KG, "15kg 이하")), List.of(), ExtractionMethod.LLM);

        MergedReading merged = ReadingMerger.merge(earlier, later);

        assertThat(merged.fields().maxWeightKg()).isNull();
        assertThat(merged.fields().leashRequired()).isTrue();
        assertThat(merged.conflicts()).hasSize(1);
        assertThat(merged.evidence()).noneMatch(e -> e.fieldName().equals(FieldNames.MAX_WEIGHT_KG));
        assertThat(merged.method()).isEqualTo(ExtractionMethod.MIXED);
    }

    @Test
    @DisplayName("두 읽기의 버린 수는 합친다")
    void 버린_수() {
        LlmReading first = new LlmReading(ConditionFields.empty(), List.of(), 1, 2, 0);
        LlmReading second = new LlmReading(ConditionFields.empty(), List.of(), 0, 1, 3);

        LlmReading merged = ReadingMerger.merge(first, second);

        assertThat(merged.droppedValues()).isEqualTo(1);
        assertThat(merged.ignoredCitations()).isEqualTo(3);
        assertThat(merged.droppedNumbers()).isEqualTo(3);
    }

    private static LlmReading reading(ConditionFields fields, Evidence... evidence) {
        return new LlmReading(fields, List.of(evidence), 0, 0, 0);
    }

    private static Evidence line(String field, String text) {
        return new Evidence(field, "etcAcmpyInfo", 1, text);
    }
}
