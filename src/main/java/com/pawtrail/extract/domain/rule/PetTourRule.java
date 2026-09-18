package com.pawtrail.extract.domain.rule;

import com.pawtrail.extract.domain.enums.Scope;
import com.pawtrail.extract.domain.model.ConditionFields;
import com.pawtrail.extract.domain.model.Evidence;
import com.pawtrail.extract.domain.model.FieldNames;
import com.pawtrail.extract.domain.model.PetTourRaw;
import com.pawtrail.extract.domain.model.RuleResult;
import com.pawtrail.extract.domain.model.SourceText;

import java.util.ArrayList;
import java.util.List;

import static com.pawtrail.extract.domain.rule.RuleTexts.addText;
import static com.pawtrail.extract.domain.rule.RuleTexts.trimToNull;

/**
 * 한국관광공사 반려동물 동반여행 원문의 규칙입니다.
 *
 * <b>동반 구분은 범위만 적습니다.</b>
 * <pre>
 * 전구역 동반가능    scope ALL_AREA
 * 일부구역 동반가능  scope PARTIAL
 * </pre>
 * 전구역을 실내 true 로 넓히지 않습니다. 전구역이면서 문화정보원이 실내 N 이라고 한 곳 40곳 중
 * 37곳이 여행지였고, 넓히면 그곳들에 가짜 충돌이 생깁니다.
 *
 * <b>동반 가능 동물이 정확히 "불가" 면 동반 불가입니다.</b>
 * scope NONE 과 실내 · 실외 false 를 함께 적습니다. 실물 4건이 모두 동반 구분이 비어 있어
 * 두 칸이 서로 다른 말을 한 적은 없습니다. 둘이 겹치면 불가를 따릅니다.
 *
 * <b>문장 칸 넷은 LLM 에 한 번에 넘깁니다.</b>
 * 서로가 서로의 문맥입니다. "전 견종 동반 가능" 652건 중 605건이 기타 동반 정보에
 * "맹견의 경우, 입마개 착용 필수" 를 함께 적고 있어, 따로 읽으면 견종 규칙이 거꾸로 나옵니다.
 * 동반 가능 동물이 "불가" 여도 넘깁니다 — 모델이 같은 말을 하는지는 소스 내 충돌 검사가 봅니다.
 */
public final class PetTourRule {

    static final String ALL_AREA = "전구역 동반가능";
    static final String PARTIAL = "일부구역 동반가능";
    static final String REFUSED = "불가";

    private PetTourRule() {
    }

    public static RuleResult extract(PetTourRaw raw) {
        ConditionFields.Builder fields = ConditionFields.builder();
        List<Evidence> evidence = new ArrayList<>();

        String possible = trimToNull(raw.acmpyPsblCpam());
        if (REFUSED.equals(possible)) {
            fields.scope(Scope.NONE).indoorAllowed(false).outdoorAllowed(false);
            evidence.add(Evidence.ofRule(FieldNames.SCOPE, PetTourRaw.KEY_POSSIBLE, possible));
            evidence.add(Evidence.ofRule(FieldNames.INDOOR_ALLOWED, PetTourRaw.KEY_POSSIBLE, possible));
            evidence.add(Evidence.ofRule(FieldNames.OUTDOOR_ALLOWED, PetTourRaw.KEY_POSSIBLE, possible));
        } else {
            String type = trimToNull(raw.acmpyTypeCd());
            Scope scope = scopeOf(type);
            if (scope != null) {
                fields.scope(scope);
                evidence.add(Evidence.ofRule(FieldNames.SCOPE, PetTourRaw.KEY_TYPE, type));
            }
        }

        List<SourceText> texts = new ArrayList<>();
        addText(texts, PetTourRaw.KEY_POSSIBLE, raw.acmpyPsblCpam());
        addText(texts, PetTourRaw.KEY_NEED, raw.acmpyNeedMtr());
        addText(texts, PetTourRaw.KEY_ETC, raw.etcAcmpyInfo());
        addText(texts, PetTourRaw.KEY_RISK, raw.relaAcdntRiskMtr());

        return RuleResult.of(fields.build(), evidence, texts);
    }

    // 실물에는 두 값뿐임
    // 다른 값이 오면 범위를 모르는 것으로 보고 비워 둠 — UNKNOWN 을 쓰지 않는 이유는 Scope 에 적어 둠
    private static Scope scopeOf(String type) {
        if (ALL_AREA.equals(type)) {
            return Scope.ALL_AREA;
        }
        if (PARTIAL.equals(type)) {
            return Scope.PARTIAL;
        }
        return null;
    }
}
