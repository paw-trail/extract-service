package com.pawtrail.extract.domain.rule;

import com.pawtrail.extract.domain.enums.RuleOutcome;
import com.pawtrail.extract.domain.enums.Scope;
import com.pawtrail.extract.domain.enums.SizeRule;
import com.pawtrail.extract.domain.model.CultureRaw;
import com.pawtrail.extract.domain.model.Evidence;
import com.pawtrail.extract.domain.model.RuleResult;
import com.pawtrail.extract.domain.model.SourceText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CultureRuleTest {

    @Test
    @DisplayName("동물병원은 보내지 않고 처리 완료로 둔다")
    void 동물병원() {
        RuleResult result = CultureRule.extract(
                raw("동물병원", "Y", "Y", "Y", "해당없음", "모두 가능", "없음", "제한사항 없음"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.SKIP_DONE);
        assertThat(result.reason()).contains("동물병원");
    }

    @Test
    @DisplayName("동반 N 은 불가이고 그 행의 채움말은 읽지 않는다")
    void 동반_불가() {
        RuleResult result = CultureRule.extract(
                raw("박물관", "N", "N", "N", "해당없음", "해당없음", "없음", "해당없음"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.SEND);
        assertThat(result.fields().scope()).isEqualTo(Scope.NONE);
        assertThat(result.fields().indoorAllowed()).isFalse();
        assertThat(result.fields().outdoorAllowed()).isFalse();
        // "없음" 은 불가라서 채운 자리이지 "추가 요금 0원" 이 아님
        assertThat(result.fields().extraFeeAmount()).isNull();
        assertThat(result.fields().petOnly()).isNull();
        assertThat(result.texts()).isEmpty();
        assertThat(result.evidence()).extracting(Evidence::segmentText)
                .containsOnly("반려동물 동반 가능정보 N");
    }

    @Test
    @DisplayName("야외만 동반 — 실내외는 칸 이름을 붙인 근거로, 제한사항은 LLM 으로")
    void 야외만() {
        RuleResult result = CultureRule.extract(
                raw("미술관", "Y", "N", "Y", "해당없음", "모두 가능", "없음", "야외만 반려동물 동반 가능, 목줄"));

        assertThat(result.fields().indoorAllowed()).isFalse();
        assertThat(result.fields().outdoorAllowed()).isTrue();
        assertThat(result.fields().sizeRule()).isEqualTo(SizeRule.ALL);
        assertThat(result.fields().extraFeeAmount()).isZero();
        assertThat(result.fields().petOnly()).isFalse();
        assertThat(result.evidence()).contains(
                Evidence.ofRule("indoorAllowed", "장소(실내) 여부", "장소(실내) 여부 N"),
                Evidence.ofRule("outdoorAllowed", "장소(실외)여부", "장소(실외)여부 Y"),
                Evidence.ofRule("sizeRule", "입장 가능 동물 크기", "모두 가능"),
                Evidence.ofRule("extraFeeAmount", "애견 동반 추가 요금", "없음"),
                Evidence.ofRule("petOnly", "반려동물 전용 정보", "해당없음"));
        assertThat(result.texts()).containsExactly(
                new SourceText("반려동물 제한사항", "야외만 반려동물 동반 가능, 목줄"));
    }

    @Test
    @DisplayName("크기 소형 · 소형/중형")
    void 크기_이름() {
        assertThat(size("소형").fields().sizeRule()).isEqualTo(SizeRule.SMALL_ONLY);
        assertThat(size("소형/중형").fields().sizeRule()).isEqualTo(SizeRule.SMALL_MEDIUM);
    }

    @Test
    @DisplayName("N kg 미만 소형 — 체중 상한 · 미만 · 소형을 함께 적는다")
    void 크기_미만_소형() {
        RuleResult result = size("5kg 미만 소형");

        assertThat(result.fields().maxWeightKg()).isEqualByComparingTo(new BigDecimal("5"));
        assertThat(result.fields().weightInclusive()).isFalse();
        assertThat(result.fields().sizeRule()).isEqualTo(SizeRule.SMALL_ONLY);
        assertThat(result.evidence()).extracting(Evidence::fieldName)
                .contains("maxWeightKg", "weightInclusive", "sizeRule");
    }

    @Test
    @DisplayName("이하와 이내는 상한을 포함하고 미만은 포함하지 않는다")
    void 크기_이하_이내_미만() {
        assertThat(size("10kg 이하").fields().weightInclusive()).isTrue();
        assertThat(size("7kg 이내").fields().weightInclusive()).isTrue();
        assertThat(size("10 KG 미만").fields().weightInclusive()).isFalse();
        assertThat(size("10kg 이하").fields().sizeRule()).isNull();
    }

    @Test
    @DisplayName("패턴 밖의 크기는 LLM 에 넘긴다")
    void 크기_꼬리() {
        RuleResult result = size("주말 및 공휴일은 13kg 이하");

        assertThat(result.fields().maxWeightKg()).isNull();
        assertThat(result.texts()).containsExactly(
                new SourceText("입장 가능 동물 크기", "주말 및 공휴일은 13kg 이하"));
    }

    @Test
    @DisplayName("요금 N원은 금액만 적고 단위는 비운다")
    void 요금_금액() {
        RuleResult result = fee("20,000원");

        assertThat(result.fields().extraFeeAmount()).isEqualTo(20000);
        assertThat(result.fields().extraFeeUnit()).isNull();
    }

    @Test
    @DisplayName("범위 요금은 LLM 에 넘긴다")
    void 요금_꼬리() {
        RuleResult result = fee("5,000~6,000원");

        assertThat(result.fields().extraFeeAmount()).isNull();
        assertThat(result.texts()).containsExactly(new SourceText("애견 동반 추가 요금", "5,000~6,000원"));
    }

    @Test
    @DisplayName("제한사항 없음은 여러 칸을 뭉뚱그린 말이라 넘기지도 채우지도 않는다")
    void 제한사항_없음() {
        RuleResult result = CultureRule.extract(
                raw("반려동물용품", "Y", "Y", "N", "해당없음", "모두 가능", "없음", "제한사항 없음"));

        assertThat(result.texts()).isEmpty();
        assertThat(result.fields().leashRequired()).isNull();
        assertThat(result.fields().carrierRequired()).isNull();
    }

    @Test
    @DisplayName("반려동물 전용은 petOnly true 다")
    void 반려동물_전용() {
        RuleResult result = CultureRule.extract(
                raw("반려동물용품", "Y", "Y", "N", "반려동물 전용", "모두 가능", "없음", "제한사항 없음"));

        assertThat(result.fields().petOnly()).isTrue();
    }

    private static RuleResult size(String size) {
        return CultureRule.extract(raw("펜션", "Y", "Y", "Y", "해당없음", size, "없음", "제한사항 없음"));
    }

    private static RuleResult fee(String fee) {
        return CultureRule.extract(raw("펜션", "Y", "Y", "Y", "해당없음", "모두 가능", fee, "제한사항 없음"));
    }

    private static CultureRaw raw(String category3, String companion, String indoor, String outdoor,
                                  String petOnly, String size, String fee, String restriction) {
        return new CultureRaw(category3, companion, indoor, outdoor, petOnly, size, fee, restriction);
    }
}
