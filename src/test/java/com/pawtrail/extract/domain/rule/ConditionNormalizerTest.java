package com.pawtrail.extract.domain.rule;

import com.pawtrail.extract.domain.enums.ExtractionMethod;
import com.pawtrail.extract.domain.enums.Scope;
import com.pawtrail.extract.domain.model.ConditionFields;
import com.pawtrail.extract.domain.model.Evidence;
import com.pawtrail.extract.domain.model.FieldNames;
import com.pawtrail.extract.domain.model.IntraConflict;
import com.pawtrail.extract.domain.model.MergedReading;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정규화 두 규칙을 봅니다. 정확도 평가에서 정답 기준과 모델 출력이 갈린 자리를 코드로 맞춘 것입니다.
 */
class ConditionNormalizerTest {

    @Test
    @DisplayName("범위가 동반 불가면 비어 있는 실내 · 실외를 불가로 채우고 범위의 근거를 옮긴다")
    void 동반_불가면_실내외도_불가() {
        MergedReading reading = reading(ConditionFields.builder().scope(Scope.NONE).build(),
                List.of(Evidence.ofLlm(FieldNames.SCOPE, "etcAcmpyInfo", 0, "반려동물 동반 불가")));

        MergedReading normalized = ConditionNormalizer.normalize(reading);

        assertThat(normalized.fields().indoorAllowed()).isFalse();
        assertThat(normalized.fields().outdoorAllowed()).isFalse();
        assertThat(normalized.evidence()).extracting(Evidence::fieldName)
                .containsExactly(FieldNames.SCOPE, FieldNames.INDOOR_ALLOWED, FieldNames.OUTDOOR_ALLOWED);
        assertThat(normalized.evidence()).extracting(Evidence::segmentText).containsOnly("반려동물 동반 불가");
    }

    @Test
    @DisplayName("허용 구역만 말하고 범위를 비워 두면 일부 구역으로 채운다")
    void 허용_구역이면_일부_구역() {
        MergedReading reading = reading(ConditionFields.builder().allowedZonesOnly(List.of("반려 구역")).build(),
                List.of(Evidence.ofLlm(FieldNames.ALLOWED_ZONES_ONLY, "intro", 2, "반려 구역에서만 동반 가능")));

        MergedReading normalized = ConditionNormalizer.normalize(reading);

        assertThat(normalized.fields().scope()).isEqualTo(Scope.PARTIAL);
        assertThat(normalized.evidence()).filteredOn(e -> e.fieldName().equals(FieldNames.SCOPE))
                .extracting(Evidence::segmentText).containsExactly("반려 구역에서만 동반 가능");
    }

    @Test
    @DisplayName("제외 구역이 있어도 일부 구역으로 채운다")
    void 제외_구역이면_일부_구역() {
        MergedReading reading = reading(ConditionFields.builder().excludedZones(List.of("수영장")).build(),
                List.of(Evidence.ofLlm(FieldNames.EXCLUDED_ZONES, "intro", 1, "수영장은 반려견 이용 불가")));

        assertThat(ConditionNormalizer.normalize(reading).fields().scope()).isEqualTo(Scope.PARTIAL);
    }

    @Test
    @DisplayName("실내만 막고 실외는 되면 일부 구역으로 채운다")
    void 실내만_막으면_일부_구역() {
        MergedReading reading = reading(ConditionFields.builder().indoorAllowed(false).outdoorAllowed(true).build(),
                List.of(Evidence.ofLlm(FieldNames.INDOOR_ALLOWED, "제한사항", 0, "야외만 동반 가능"),
                        Evidence.ofLlm(FieldNames.OUTDOOR_ALLOWED, "제한사항", 0, "야외만 동반 가능")));

        MergedReading normalized = ConditionNormalizer.normalize(reading);

        assertThat(normalized.fields().scope()).isEqualTo(Scope.PARTIAL);
        // 같은 문구는 한 번만 옮김
        assertThat(normalized.evidence()).filteredOn(e -> e.fieldName().equals(FieldNames.SCOPE)).hasSize(1);
    }

    @Test
    @DisplayName("규칙이 읽은 실내 불가 · 실외 가능은 일부 구역으로 채우지 않는다")
    void 규칙이_읽은_실외만은_그대로() {
        // 한국문화정보원 실내 N · 실외 Y — 실내만 되는 곳(실내 Y · 실외 N)과 같게 범위를 비워 둠
        MergedReading reading = reading(ConditionFields.builder().indoorAllowed(false).outdoorAllowed(true).build(),
                List.of(Evidence.ofRule(FieldNames.INDOOR_ALLOWED, "장소(실내) 여부", "장소(실내) 여부 N"),
                        Evidence.ofRule(FieldNames.OUTDOOR_ALLOWED, "장소(실외)여부", "장소(실외)여부 Y")));

        assertThat(ConditionNormalizer.normalize(reading).fields().scope()).isNull();
    }

    @Test
    @DisplayName("실내 · 실외 중 한쪽만 모델이 읽었으면 채우지 않는다")
    void 한쪽만_모델이면_그대로() {
        MergedReading reading = reading(ConditionFields.builder().indoorAllowed(false).outdoorAllowed(true).build(),
                List.of(Evidence.ofRule(FieldNames.INDOOR_ALLOWED, "장소(실내) 여부", "장소(실내) 여부 N"),
                        Evidence.ofLlm(FieldNames.OUTDOOR_ALLOWED, "반려동물 제한사항", 0, "야외 동반 가능")));

        assertThat(ConditionNormalizer.normalize(reading).fields().scope()).isNull();
    }

    @Test
    @DisplayName("옮겨 적는 근거는 원래 근거의 추출 방식을 그대로 쓴다")
    void 옮긴_근거의_방식() {
        MergedReading reading = reading(ConditionFields.builder().scope(Scope.NONE).build(),
                List.of(Evidence.ofRule(FieldNames.SCOPE, "animalCmgCl", "불가능")));

        MergedReading normalized = ConditionNormalizer.normalize(reading);

        assertThat(normalized.evidence()).extracting(Evidence::fieldName)
                .containsExactly(FieldNames.SCOPE, FieldNames.INDOOR_ALLOWED, FieldNames.OUTDOOR_ALLOWED);
        assertThat(normalized.evidence()).extracting(Evidence::extractionMethod)
                .containsOnly(ExtractionMethod.RULE);
    }

    @Test
    @DisplayName("이미 값이 있는 칸은 덮지 않는다")
    void 있는_값은_그대로() {
        MergedReading reading = reading(ConditionFields.builder()
                .scope(Scope.ALL_AREA).excludedZones(List.of("실내")).build(), List.of());

        assertThat(ConditionNormalizer.normalize(reading).fields().scope()).isEqualTo(Scope.ALL_AREA);
    }

    @Test
    @DisplayName("소스 내 충돌로 비운 범위는 다시 채우지 않는다")
    void 충돌로_비운_칸은_그대로() {
        MergedReading reading = new MergedReading(
                ConditionFields.builder().excludedZones(List.of("실내")).build(),
                List.of(),
                List.of(new IntraConflict(FieldNames.SCOPE, "전구역 동반가능", "실내는 동반 불가")),
                ExtractionMethod.MIXED);

        MergedReading normalized = ConditionNormalizer.normalize(reading);

        // 두 쪽이 갈려 비운 것인데 한쪽에서 따라 나온 값을 넣으면 그 판단을 뒤집게 됨
        assertThat(normalized.fields().scope()).isNull();
        assertThat(normalized.conflicts()).hasSize(1);
    }

    @Test
    @DisplayName("채울 것이 없으면 그대로 돌려준다")
    void 채울_것_없음() {
        MergedReading reading = reading(ConditionFields.builder().leashRequired(true).build(),
                List.of(Evidence.ofLlm(FieldNames.LEASH_REQUIRED, "t", 0, "목줄 착용")));

        MergedReading normalized = ConditionNormalizer.normalize(reading);

        assertThat(normalized.fields()).isEqualTo(reading.fields());
        assertThat(normalized.evidence()).isEqualTo(reading.evidence());
    }

    private static MergedReading reading(ConditionFields fields, List<Evidence> evidence) {
        return new MergedReading(fields, evidence, List.of(), ExtractionMethod.LLM);
    }
}
