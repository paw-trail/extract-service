package com.pawtrail.extract.domain.enums;

/**
 * 견종 규칙입니다. 값 이름은 policy 의 BreedRule 과 같습니다.
 *
 * 규칙 추출은 이 칸을 채우지 않습니다. 견종 규칙은 기타 동반 정보 같은 문장에서 나오므로
 * LLM 추출이 채웁니다.
 */
public enum BreedRule {

    NONE,
    DANGEROUS_MUZZLE,
    DANGEROUS_BANNED
}
