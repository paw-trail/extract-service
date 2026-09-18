package com.pawtrail.extract.domain.rule;

import com.pawtrail.extract.domain.enums.RuleOutcome;
import com.pawtrail.extract.domain.enums.Scope;
import com.pawtrail.extract.domain.model.Evidence;
import com.pawtrail.extract.domain.model.PetTourRaw;
import com.pawtrail.extract.domain.model.RuleResult;
import com.pawtrail.extract.domain.model.SourceText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PetTourRuleTest {

    @Test
    @DisplayName("전구역은 범위만 적고 실내외로 넓히지 않는다")
    void 전구역() {
        RuleResult result = PetTourRule.extract(new PetTourRaw("전구역 동반가능", null, null, null, null));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.SEND);
        assertThat(result.fields().scope()).isEqualTo(Scope.ALL_AREA);
        assertThat(result.fields().indoorAllowed()).isNull();
        assertThat(result.fields().outdoorAllowed()).isNull();
        assertThat(result.evidence()).containsExactly(
                Evidence.ofRule("scope", "acmpyTypeCd", "전구역 동반가능"));
        assertThat(result.texts()).isEmpty();
    }

    @Test
    @DisplayName("일부구역은 PARTIAL 이다")
    void 일부구역() {
        RuleResult result = PetTourRule.extract(new PetTourRaw("일부구역 동반가능", null, null, null, null));

        assertThat(result.fields().scope()).isEqualTo(Scope.PARTIAL);
    }

    @Test
    @DisplayName("동반 가능 동물이 정확히 불가면 범위 NONE 과 실내외 false 를 함께 적는다")
    void 불가() {
        RuleResult result = PetTourRule.extract(new PetTourRaw(null, "불가", null, null, null));

        assertThat(result.fields().scope()).isEqualTo(Scope.NONE);
        assertThat(result.fields().indoorAllowed()).isFalse();
        assertThat(result.fields().outdoorAllowed()).isFalse();
        assertThat(result.evidence()).containsExactly(
                Evidence.ofRule("scope", "acmpyPsblCpam", "불가"),
                Evidence.ofRule("indoorAllowed", "acmpyPsblCpam", "불가"),
                Evidence.ofRule("outdoorAllowed", "acmpyPsblCpam", "불가"));
        // 불가여도 문장 칸으로 넘김 — 모델이 같은 말을 하는지는 소스 내 충돌 검사가 봄
        assertThat(result.texts()).containsExactly(new SourceText("acmpyPsblCpam", "불가"));
    }

    @Test
    @DisplayName("불가와 동반 구분이 겹치면 불가를 따른다")
    void 불가가_앞선다() {
        RuleResult result = PetTourRule.extract(new PetTourRaw("전구역 동반가능", "불가", null, null, null));

        assertThat(result.fields().scope()).isEqualTo(Scope.NONE);
    }

    @Test
    @DisplayName("문장 칸 넷을 정해진 순서로 넘기고 빈 칸은 뺀다")
    void 문장_칸() {
        RuleResult result = PetTourRule.extract(new PetTourRaw(
                "일부구역 동반가능", "전 견종 동반 가능", null,
                "- 맹견의 경우, 입마개 착용 필수\n- 배변봉투 지참", "   "));

        assertThat(result.texts()).containsExactly(
                new SourceText("acmpyPsblCpam", "전 견종 동반 가능"),
                new SourceText("etcAcmpyInfo", "- 맹견의 경우, 입마개 착용 필수\n- 배변봉투 지참"));
    }

    @Test
    @DisplayName("모르는 동반 구분은 비워 둔다")
    void 모르는_동반_구분() {
        RuleResult result = PetTourRule.extract(new PetTourRaw("알 수 없음", null, null, null, null));

        assertThat(result.fields().scope()).isNull();
        assertThat(result.outcome()).isEqualTo(RuleOutcome.EMPTY);
    }

    @Test
    @DisplayName("petTour 블록이 없거나 비어 있으면 빈 문서다")
    void 블록_없음() {
        RuleResult result = PetTourRule.extract(PetTourRaw.withoutBlock());

        assertThat(result.outcome()).isEqualTo(RuleOutcome.EMPTY);
        assertThat(result.fields().isEmpty()).isTrue();
        assertThat(result.evidence()).isEmpty();
    }
}
