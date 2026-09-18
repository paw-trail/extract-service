package com.pawtrail.extract.domain.enums;

/**
 * 입장 가능한 크기 규칙입니다. 값 이름은 policy 의 SizeRule 과 같습니다.
 *
 * ALL 은 "크기 제한 없음" 이라는 값입니다. 정보가 없다는 뜻이 아니며,
 * 정보가 없으면 칸을 비워 둡니다.
 */
public enum SizeRule {

    SMALL_ONLY,
    SMALL_MEDIUM,
    ALL
}
