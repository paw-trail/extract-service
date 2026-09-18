package com.pawtrail.extract.domain.enums;

/**
 * 원문 한 건에 대한 규칙 추출의 결과 갈래입니다.
 *
 * <pre>
 * SEND       규칙 칸이나 LLM 에 넘길 원문이 하나라도 있음 — 이어서 처리해 policy 로 보냄
 * EMPTY      규칙 칸도 넘길 원문도 없음 — 20칸이 빈 행으로 보냄
 * SKIP_DONE  판정 대상이 아님 — policy 로 보내지 않고 처리 완료로 둠
 * FAILED     원문에 약속한 키가 없음 — 처리 실패로 둠
 * </pre>
 *
 * EMPTY 를 보내는 이유는 policy 에서 "20칸이 빈 행" 이 "뽑았으나 조건을 못 찾음" 을 뜻하기 때문입니다.
 * 보내지 않으면 원문이 조건을 지웠을 때 예전 조건이 그대로 남습니다.
 * policy 에 출처 행을 지우는 API 가 없습니다.
 */
public enum RuleOutcome {

    SEND,
    EMPTY,
    SKIP_DONE,
    FAILED
}
