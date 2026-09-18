package com.pawtrail.extract.domain.rule;

import com.pawtrail.extract.domain.enums.RuleOutcome;
import com.pawtrail.extract.domain.enums.Scope;
import com.pawtrail.extract.domain.enums.SizeRule;
import com.pawtrail.extract.domain.model.Evidence;
import com.pawtrail.extract.domain.model.GoCampingRaw;
import com.pawtrail.extract.domain.model.RuleResult;
import com.pawtrail.extract.domain.model.SourceText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GoCampingRuleTest {

    @Test
    @DisplayName("불가능은 범위 NONE 과 실내외 false 다")
    void 불가능() {
        RuleResult result = GoCampingRule.extract(new GoCampingRaw("불가능", Map.of()));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.SEND);
        assertThat(result.fields().scope()).isEqualTo(Scope.NONE);
        assertThat(result.fields().indoorAllowed()).isFalse();
        assertThat(result.fields().outdoorAllowed()).isFalse();
        assertThat(result.evidence()).containsExactly(
                Evidence.ofRule("scope", "animalCmgCl", "불가능"),
                Evidence.ofRule("indoorAllowed", "animalCmgCl", "불가능"),
                Evidence.ofRule("outdoorAllowed", "animalCmgCl", "불가능"));
    }

    @Test
    @DisplayName("가능은 실외 true 만 적고 실내는 비워 둔다")
    void 가능() {
        RuleResult result = GoCampingRule.extract(new GoCampingRaw("가능", Map.of()));

        assertThat(result.fields().outdoorAllowed()).isTrue();
        assertThat(result.fields().indoorAllowed()).isNull();
        assertThat(result.fields().scope()).isNull();
        assertThat(result.evidence()).containsExactly(
                Evidence.ofRule("outdoorAllowed", "animalCmgCl", "가능"));
    }

    @Test
    @DisplayName("가능(소형견)은 실외 true 와 SMALL_ONLY 다")
    void 가능_소형견() {
        RuleResult result = GoCampingRule.extract(new GoCampingRaw("가능(소형견)", Map.of()));

        assertThat(result.fields().outdoorAllowed()).isTrue();
        assertThat(result.fields().sizeRule()).isEqualTo(SizeRule.SMALL_ONLY);
        assertThat(result.evidence()).extracting(Evidence::fieldName)
                .containsExactly("outdoorAllowed", "sizeRule");
    }

    @Test
    @DisplayName("출입 칸이 없고 반려 낱말도 없으면 빈 문서다")
    void 빈_문서() {
        RuleResult result = GoCampingRule.extract(new GoCampingRaw(null, texts(
                "intro", "숲속에 자리한 오토캠핑장입니다.")));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.EMPTY);
    }

    @Test
    @DisplayName("반려 낱말이 든 글 칸만 순서대로 넘긴다")
    void 반려_낱말이_든_칸만() {
        RuleResult result = GoCampingRule.extract(new GoCampingRaw(null, texts(
                "facltNm", "양양 댕댕캠프",
                "intro", "바다가 보이는 캠핑장입니다.",
                "tooltip", "소형견만 동반 가능")));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.SEND);
        assertThat(result.fields().isEmpty()).isTrue();
        assertThat(result.texts()).containsExactly(
                new SourceText("facltNm", "양양 댕댕캠프"),
                new SourceText("tooltip", "소형견만 동반 가능"));
    }

    @Test
    @DisplayName("크기 · 견종 낱말로도 걸린다")
    void 크기_견종_낱말() {
        assertThat(GoCampingRule.mentionsPet("대형견은 입장할 수 없습니다")).isTrue();
        assertThat(GoCampingRule.mentionsPet("맹견 출입 금지")).isTrue();
        assertThat(GoCampingRule.mentionsPet("개수대와 화장실이 있습니다")).isFalse();
    }

    private static Map<String, String> texts(String... keyValues) {
        Map<String, String> texts = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            texts.put(keyValues[i], keyValues[i + 1]);
        }
        return texts;
    }
}
