package com.pawtrail.extract.domain.rule;

import com.pawtrail.extract.domain.model.CultureRaw;
import com.pawtrail.extract.domain.model.GoCampingRaw;
import com.pawtrail.extract.domain.model.PetTourRaw;
import com.pawtrail.extract.domain.model.RuleResult;
import com.pawtrail.extract.domain.model.SourceRaw;

/**
 * 원문 한 건을 소스에 맞는 규칙으로 보냅니다.
 *
 * 스프링을 모르는 순수 계산입니다. 원문을 받아 오고 결과를 보내는 일은 실행 쪽이 맡습니다.
 * 나눠 둔 덕에 외부 호출 없이 규칙을 단위 테스트로 검증할 수 있습니다.
 *
 * <b>규칙이 채우는 칸과 LLM 에 넘기는 칸이 소스마다 갈립니다.</b>
 * <pre>
 * 공사       규칙   동반 구분 · 동반 가능 동물이 정확히 "불가"
 *            LLM    동반 가능 동물 · 필요사항 · 기타 동반 정보 · 사고 대비
 * 고캠핑      규칙   반려동물 출입
 *            LLM    반려 낱말이 든 글 칸
 * 문화정보원   규칙   동반 · 실내 · 실외 · 전용 · 크기와 요금의 단순 패턴
 *            LLM    제한사항 · 패턴 밖의 크기와 요금
 * </pre>
 * 정형 칸은 규칙이 읽어야 이하 · 미만 같은 구분이 흔들리지 않고,
 * 문장은 앞뒤 문맥과 함께 읽어야 뜻이 맞아 LLM 에 맡깁니다.
 */
public final class ConditionRules {

    private ConditionRules() {
    }

    public static RuleResult extract(SourceRaw raw) {
        return switch (raw) {
            case PetTourRaw petTour -> PetTourRule.extract(petTour);
            case GoCampingRaw goCamping -> GoCampingRule.extract(goCamping);
            case CultureRaw culture -> CultureRule.extract(culture);
        };
    }
}
