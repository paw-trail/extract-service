package com.pawtrail.extract.domain.enums;

/**
 * 동반 범위입니다.
 *
 * 값 이름은 policy 의 Scope 와 같습니다. 다만 UNKNOWN 은 두지 않았습니다.
 * UNKNOWN 은 "뽑아 봤으나 모름" 이라는 값이라 다른 소스의 전구역 · 일부구역과 비교되어
 * 가짜 충돌을 만듭니다. 범위를 모르면 이 서비스는 값을 비워 둡니다.
 *
 * <b>NONE 은 "동반 불가" 입니다.</b>
 * 불가를 실내 · 실외 false 로만 적으면 다른 소스의 전구역 · 일부구역과 칸이 달라
 * 가부가 정면으로 갈려도 충돌로 잡히지 않습니다. 그래서 불가는 이 칸에도 함께 적습니다.
 * policy 에는 선행 이슈에서 더해질 값이므로, 이 값을 싣고 보내는 실행 이슈는 그 선행 이슈 뒤에 옵니다.
 */
public enum Scope {

    ALL_AREA,
    PARTIAL,
    NONE
}
