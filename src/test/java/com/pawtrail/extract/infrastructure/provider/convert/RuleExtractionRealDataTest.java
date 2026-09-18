package com.pawtrail.extract.infrastructure.provider.convert;

import com.pawtrail.extract.domain.enums.RuleOutcome;
import com.pawtrail.extract.domain.enums.Scope;
import com.pawtrail.extract.domain.enums.SizeRule;
import com.pawtrail.extract.domain.enums.SourceType;
import com.pawtrail.extract.domain.model.ConditionFields;
import com.pawtrail.extract.domain.model.RuleResult;
import com.pawtrail.extract.domain.model.SourceText;
import com.pawtrail.extract.domain.provider.PayloadReader;
import com.pawtrail.extract.domain.rule.ConditionRules;
import com.pawtrail.extract.support.RawFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 원문 표본을 읽기부터 규칙까지 통째로 돌립니다.
 *
 * 규칙 자체는 규칙 테스트가 고정하고 여기는 실물입니다.
 * 이 파일이 깨지면 규칙이 틀린 것이 아니라 원문에 대한 이해가 틀린 것입니다.
 *
 * <b>실측 기준 (2026.9.13 원문 덤프 · 17,480행)</b>
 * <pre>
 * 공사        1,079   보냄 1,072 · 빈 문서 7 (petTour 블록이 {} 로 비어 있음)
 *                     범위 1,052 (전구역 · 일부구역 1,048 · 불가 4)
 *                     넘길 원문이 있는 문서 996 · 서로 다른 입력 550
 * 고캠핑       2,993   보냄 2,495 · 빈 문서 498
 *                     불가능 1,385 · 실외 true 1,103 (가능 658 · 가능(소형견) 445)
 *                     넘길 원문이 있는 문서 328
 * 문화정보원  13,408   보내지 않고 처리 완료 4,488 (동물병원) · 보냄 8,920
 *                     동반 불가 2,781 · 크기 5,797 · 체중 상한 429 · 요금 6,104 · 전용 6,139
 *                     넘길 원문이 있는 문서 1,618 · 서로 다른 입력 277
 * </pre>
 * 고캠핑 빈 문서가 착수 때 센 499 가 아니라 498 인 것은 거름 낱말에 크기 · 견종 낱말을 더해
 * 한 건이 더 걸렸기 때문입니다.
 *
 * 표본은 유형마다 한 건씩 덤프에서 골라 payload 를 그대로 옮겼습니다.
 */
class RuleExtractionRealDataTest {

    private final Map<SourceType, PayloadReader> readers = Map.of(
            SourceType.PET_TOUR, new PetTourPayloadReader(),
            SourceType.GOCAMPING, new GoCampingPayloadReader(),
            SourceType.CULTURE_CSV, new CulturePayloadReader());

    @Test
    @DisplayName("공사 — 일부구역과 문장 칸 셋 (익선동 한옥거리)")
    void 공사_익선동() {
        RuleResult result = extract(SourceType.PET_TOUR, "pet-tour/ikseondong");

        assertThat(result.outcome()).isEqualTo(RuleOutcome.SEND);
        assertThat(result.fields().scope()).isEqualTo(Scope.PARTIAL);
        assertThat(result.texts()).extracting(SourceText::originField)
                .containsExactly("acmpyPsblCpam", "acmpyNeedMtr", "etcAcmpyInfo");
    }

    @Test
    @DisplayName("공사 — 전구역이면서 본문은 실내 불가 · 규칙은 범위만 적는다")
    void 공사_전구역_실내_불가() {
        RuleResult result = extract(SourceType.PET_TOUR, "pet-tour/all-area-indoor-banned");

        assertThat(result.fields().scope()).isEqualTo(Scope.ALL_AREA);
        assertThat(result.fields().indoorAllowed()).isNull();
        assertThat(result.texts()).hasSize(4);
    }

    @Test
    @DisplayName("공사 — 일부구역과 문장 칸 넷")
    void 공사_일부구역() {
        RuleResult result = extract(SourceType.PET_TOUR, "pet-tour/partial-area");

        assertThat(result.fields().scope()).isEqualTo(Scope.PARTIAL);
        assertThat(result.texts()).hasSize(4);
    }

    @Test
    @DisplayName("공사 — 동반 구분이 없으면 문장 칸만 넘긴다")
    void 공사_동반_구분_없음() {
        RuleResult result = extract(SourceType.PET_TOUR, "pet-tour/no-type");

        assertThat(result.outcome()).isEqualTo(RuleOutcome.SEND);
        assertThat(result.fields().isEmpty()).isTrue();
        assertThat(result.texts()).extracting(SourceText::originField)
                .containsExactly("acmpyPsblCpam", "acmpyNeedMtr", "etcAcmpyInfo");
    }

    @Test
    @DisplayName("공사 — 동반 가능 동물 불가")
    void 공사_불가() {
        RuleResult result = extract(SourceType.PET_TOUR, "pet-tour/refused");

        assertRefused(result.fields());
        assertThat(result.texts()).containsExactly(new SourceText("acmpyPsblCpam", "불가"));
    }

    @Test
    @DisplayName("공사 — petTour 블록이 {} 로 비어 있으면 빈 문서")
    void 공사_빈_블록() {
        assertThat(extract(SourceType.PET_TOUR, "pet-tour/empty-block").outcome()).isEqualTo(RuleOutcome.EMPTY);
    }

    @Test
    @DisplayName("고캠핑 — 불가능 · 반려 낱말 없음")
    void 고캠핑_불가능() {
        RuleResult result = extract(SourceType.GOCAMPING, "gocamping/refused");

        assertRefused(result.fields());
        assertThat(result.texts()).isEmpty();
    }

    @Test
    @DisplayName("고캠핑 — 가능 · 반려 낱말 없음")
    void 고캠핑_가능() {
        RuleResult result = extract(SourceType.GOCAMPING, "gocamping/allowed");

        assertThat(result.fields().outdoorAllowed()).isTrue();
        assertThat(result.fields().indoorAllowed()).isNull();
        assertThat(result.texts()).isEmpty();
    }

    @Test
    @DisplayName("고캠핑 — 가능(소형견)과 툴팁 (어반파크 캠핑장&바베큐)")
    void 고캠핑_어반파크() {
        RuleResult result = extract(SourceType.GOCAMPING, "gocamping/small-dogs-tooltip");

        assertThat(result.fields().outdoorAllowed()).isTrue();
        assertThat(result.fields().sizeRule()).isEqualTo(SizeRule.SMALL_ONLY);
        assertThat(result.texts()).extracting(SourceText::originField).containsExactly("tooltip");
    }

    @Test
    @DisplayName("고캠핑 — 출입 칸도 반려 낱말도 없으면 빈 문서")
    void 고캠핑_빈_문서() {
        assertThat(extract(SourceType.GOCAMPING, "gocamping/no-field-no-text").outcome())
                .isEqualTo(RuleOutcome.EMPTY);
    }

    @Test
    @DisplayName("고캠핑 — 출입 칸은 없고 이름에 댕댕 (양양 댕댕캠프)")
    void 고캠핑_이름만() {
        RuleResult result = extract(SourceType.GOCAMPING, "gocamping/no-field-text");

        assertThat(result.outcome()).isEqualTo(RuleOutcome.SEND);
        assertThat(result.fields().isEmpty()).isTrue();
        assertThat(result.texts()).extracting(SourceText::originField).containsExactly("facltNm");
    }

    @Test
    @DisplayName("고캠핑 — 불가능인데 소개는 동반 가능 · 소스 내 충돌 후보")
    void 고캠핑_불가능_본문_가능() {
        RuleResult result = extract(SourceType.GOCAMPING, "gocamping/refused-text");

        assertRefused(result.fields());
        assertThat(result.texts()).extracting(SourceText::originField).containsExactly("intro");
    }

    @Test
    @DisplayName("문화정보원 — 동물병원은 보내지 않는다")
    void 문화정보원_동물병원() {
        assertThat(extract(SourceType.CULTURE_CSV, "culture/vet").outcome()).isEqualTo(RuleOutcome.SKIP_DONE);
    }

    @Test
    @DisplayName("문화정보원 — 동반 N 이면 요금 없음도 읽지 않는다")
    void 문화정보원_동반_불가() {
        RuleResult result = extract(SourceType.CULTURE_CSV, "culture/refused");

        assertRefused(result.fields());
        assertThat(result.fields().extraFeeAmount()).isNull();
        assertThat(result.texts()).isEmpty();
    }

    @Test
    @DisplayName("문화정보원 — 야외만 (경기도미술관)")
    void 문화정보원_야외만() {
        RuleResult result = extract(SourceType.CULTURE_CSV, "culture/outdoor-only");

        assertThat(result.fields().indoorAllowed()).isFalse();
        assertThat(result.fields().outdoorAllowed()).isTrue();
        assertThat(result.fields().sizeRule()).isEqualTo(SizeRule.ALL);
        assertThat(result.fields().extraFeeAmount()).isZero();
        assertThat(result.fields().petOnly()).isFalse();
        assertThat(result.texts()).containsExactly(
                new SourceText("반려동물 제한사항", "야외만 반려동물 동반 가능, 목줄"));
    }

    @Test
    @DisplayName("문화정보원 — 가장 흔한 모양은 넘길 원문이 없다")
    void 문화정보원_흔한_모양() {
        RuleResult result = extract(SourceType.CULTURE_CSV, "culture/plain");

        assertThat(result.fields().indoorAllowed()).isTrue();
        assertThat(result.fields().outdoorAllowed()).isFalse();
        assertThat(result.fields().sizeRule()).isEqualTo(SizeRule.ALL);
        assertThat(result.texts()).isEmpty();
    }

    @Test
    @DisplayName("문화정보원 — 크기 소형 · 소형/중형")
    void 문화정보원_크기_이름() {
        assertThat(extract(SourceType.CULTURE_CSV, "culture/size-small").fields().sizeRule())
                .isEqualTo(SizeRule.SMALL_ONLY);
        assertThat(extract(SourceType.CULTURE_CSV, "culture/size-small-medium").fields().sizeRule())
                .isEqualTo(SizeRule.SMALL_MEDIUM);
    }

    @Test
    @DisplayName("문화정보원 — 5kg 미만 소형 (롯데마트 강변점)")
    void 문화정보원_미만_소형() {
        ConditionFields fields = extract(SourceType.CULTURE_CSV, "culture/size-kg-under-small").fields();

        assertThat(fields.maxWeightKg()).isEqualByComparingTo(new BigDecimal("5"));
        assertThat(fields.weightInclusive()).isFalse();
        assertThat(fields.sizeRule()).isEqualTo(SizeRule.SMALL_ONLY);
    }

    @Test
    @DisplayName("문화정보원 — 10kg 이하와 요금 20,000원")
    void 문화정보원_이하() {
        ConditionFields fields = extract(SourceType.CULTURE_CSV, "culture/size-kg-at-most").fields();

        assertThat(fields.maxWeightKg()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(fields.weightInclusive()).isTrue();
        assertThat(fields.extraFeeAmount()).isEqualTo(20000);
    }

    @Test
    @DisplayName("문화정보원 — 주말 조건이 붙은 크기는 LLM 에 넘긴다")
    void 문화정보원_크기_꼬리() {
        RuleResult result = extract(SourceType.CULTURE_CSV, "culture/size-tail");

        assertThat(result.fields().maxWeightKg()).isNull();
        assertThat(result.fields().petOnly()).isTrue();
        assertThat(result.texts()).containsExactly(
                new SourceText("입장 가능 동물 크기", "주말 및 공휴일은 13kg 이하"));
    }

    @Test
    @DisplayName("문화정보원 — 10kg 미만과 요금 20,000원 · 제한사항은 LLM 으로")
    void 문화정보원_요금() {
        RuleResult result = extract(SourceType.CULTURE_CSV, "culture/fee-amount");

        assertThat(result.fields().maxWeightKg()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(result.fields().weightInclusive()).isFalse();
        assertThat(result.fields().extraFeeAmount()).isEqualTo(20000);
        assertThat(result.texts()).extracting(SourceText::originField).containsExactly("반려동물 제한사항");
    }

    @Test
    @DisplayName("문화정보원 — 범위 요금은 LLM 에 넘긴다")
    void 문화정보원_요금_꼬리() {
        RuleResult result = extract(SourceType.CULTURE_CSV, "culture/fee-tail");

        assertThat(result.fields().extraFeeAmount()).isNull();
        assertThat(result.texts()).containsExactly(new SourceText("애견 동반 추가 요금", "5,000~6,000원"));
    }

    @Test
    @DisplayName("문화정보원 — 반려동물 전용")
    void 문화정보원_전용() {
        assertThat(extract(SourceType.CULTURE_CSV, "culture/pet-only").fields().petOnly()).isTrue();
    }

    private RuleResult extract(SourceType source, String fixture) {
        return ConditionRules.extract(readers.get(source).read(RawFixtures.load(fixture)));
    }

    private static void assertRefused(ConditionFields fields) {
        assertThat(fields.scope()).isEqualTo(Scope.NONE);
        assertThat(fields.indoorAllowed()).isFalse();
        assertThat(fields.outdoorAllowed()).isFalse();
    }
}
