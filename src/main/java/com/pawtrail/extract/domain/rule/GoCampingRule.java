package com.pawtrail.extract.domain.rule;

import com.pawtrail.extract.domain.enums.Scope;
import com.pawtrail.extract.domain.enums.SizeRule;
import com.pawtrail.extract.domain.model.ConditionFields;
import com.pawtrail.extract.domain.model.Evidence;
import com.pawtrail.extract.domain.model.FieldNames;
import com.pawtrail.extract.domain.model.GoCampingRaw;
import com.pawtrail.extract.domain.model.RuleResult;
import com.pawtrail.extract.domain.model.SourceText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.pawtrail.extract.domain.rule.RuleTexts.trimToNull;

/**
 * 고캠핑 원문의 규칙입니다.
 *
 * <b>반려동물 출입을 실외로 옮깁니다.</b>
 * <pre>
 * 불가능         scope NONE · 실내 false · 실외 false
 * 가능           실외 true
 * 가능(소형견)    실외 true · sizeRule SMALL_ONLY
 * </pre>
 * 야영장은 부지가 곧 실외라 "가능" 을 실외 true 로 읽습니다.
 * 카라반 · 글램핑 안쪽은 원문이 말하지 않으므로 실내는 비워 둡니다.
 *
 * <b>글 칸은 반려 낱말이 든 것만 LLM 에 넘깁니다.</b>
 * 반려 얘기가 없는 본문을 넘기면 호출만 늘고 모델이 조건을 지어낼 여지가 생깁니다.
 * 칸 단위로 거르는 이유 — 실측에서 낱말은 없는데 kg · 소형 · 목줄 · 입마개가 든 칸이 0건이었고,
 * 칸 단위로 거르면 넘길 글자가 23% 줄었습니다.
 */
public final class GoCampingRule {

    static final String REFUSED = "불가능";
    static final String ALLOWED = "가능";
    static final String SMALL_ONLY = "가능(소형견)";

    /**
     * 반려 얘기를 가려내는 낱말입니다.
     *
     * 앞의 여섯으로 거른 문서가 307건이었고, 크기 · 견종 낱말을 더하자
     * "소형견에 한해 출입 가능" 같은 문서가 23건 더 걸렸습니다.
     * "견주어도" 처럼 엉뚱하게 걸리는 것은 LLM 이 빈 결과를 내므로 해가 없습니다.
     */
    static final List<String> PET_WORDS = List.of(
            "반려", "애견", "강아지", "반려견", "펫", "애완",
            "소형견", "중형견", "대형견", "맹견", "댕댕", "도그");

    private GoCampingRule() {
    }

    public static RuleResult extract(GoCampingRaw raw) {
        ConditionFields.Builder fields = ConditionFields.builder();
        List<Evidence> evidence = new ArrayList<>();

        String animal = trimToNull(raw.animalCmgCl());
        if (REFUSED.equals(animal)) {
            fields.scope(Scope.NONE).indoorAllowed(false).outdoorAllowed(false);
            evidence.add(Evidence.ofRule(FieldNames.SCOPE, GoCampingRaw.KEY_ANIMAL, animal));
            evidence.add(Evidence.ofRule(FieldNames.INDOOR_ALLOWED, GoCampingRaw.KEY_ANIMAL, animal));
            evidence.add(Evidence.ofRule(FieldNames.OUTDOOR_ALLOWED, GoCampingRaw.KEY_ANIMAL, animal));
        } else if (ALLOWED.equals(animal)) {
            fields.outdoorAllowed(true);
            evidence.add(Evidence.ofRule(FieldNames.OUTDOOR_ALLOWED, GoCampingRaw.KEY_ANIMAL, animal));
        } else if (SMALL_ONLY.equals(animal)) {
            fields.outdoorAllowed(true).sizeRule(SizeRule.SMALL_ONLY);
            evidence.add(Evidence.ofRule(FieldNames.OUTDOOR_ALLOWED, GoCampingRaw.KEY_ANIMAL, animal));
            evidence.add(Evidence.ofRule(FieldNames.SIZE_RULE, GoCampingRaw.KEY_ANIMAL, animal));
        }

        List<SourceText> texts = new ArrayList<>();
        for (Map.Entry<String, String> entry : raw.texts().entrySet()) {
            String text = trimToNull(entry.getValue());
            if (text != null && mentionsPet(text)) {
                texts.add(new SourceText(entry.getKey(), text));
            }
        }

        return RuleResult.of(fields.build(), evidence, texts);
    }

    static boolean mentionsPet(String text) {
        for (String word : PET_WORDS) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
