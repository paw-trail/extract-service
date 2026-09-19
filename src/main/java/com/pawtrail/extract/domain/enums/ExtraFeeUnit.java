package com.pawtrail.extract.domain.enums;

/**
 * 추가 요금의 단위입니다. 값 이름은 policy 의 ExtraFeeUnit 과 같습니다.
 *
 * 문화정보원의 "N원" 은 마리당인지 1박당인지 말하지 않으므로 규칙 추출은 이 칸을 비워 둡니다.
 */
public enum ExtraFeeUnit {

    PER_DOG,
    PER_NIGHT,
    PER_VISIT
}
