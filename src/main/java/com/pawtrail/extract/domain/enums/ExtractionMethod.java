package com.pawtrail.extract.domain.enums;

/**
 * 조건을 무엇으로 뽑았는지입니다. policy bulk 요청의 extractionMethod 와 이름이 같습니다.
 *
 * <pre>
 * RULE    규칙이 정형 칸만 읽음 — 조건이 하나도 없는 빈 행도 여기
 * LLM     모델이 문장만 읽음
 * MIXED   한 문서에서 규칙과 모델이 함께 읽음
 * </pre>
 *
 * 합친 결과가 아니라 읽은 쪽을 봅니다. 두 쪽이 같은 칸에서 갈려 그 칸을 비웠어도
 * 둘 다 읽었으므로 MIXED 입니다.
 * policy 에는 MANUAL 도 있으나 관리자 정정 몫이라 extract 는 쓰지 않습니다.
 */
public enum ExtractionMethod {
    RULE,
    LLM,
    MIXED
}
