package com.pawtrail.extract.domain.model;

/**
 * 한 원문 안에서 정형 칸과 본문 문장이 같은 조건을 다르게 말한 자리입니다.
 *
 * policy bulk 요청의 소스 내 충돌 한 줄이 되며, 두 값은 sourceValues 의 field · text 키로 나갑니다.
 *
 * <pre>
 * fieldName   어긋난 칸 — 가부(범위 · 실내 · 실외)가 통째로 갈렸으면 scope 한 줄
 * fieldText   정형 칸이 말한 것 — 규칙 근거의 원문 값
 * bodyText    본문이 말한 것 — 모델이 짚은 조각, 여럿이면 " / " 로 이음
 * </pre>
 *
 * 둘 다 원문 그대로입니다. 사용자 화면의 충돌 목록에 이 글이 그대로 뜨므로
 * 우리가 만든 말이나 값 이름(PARTIAL 같은)을 넣지 않습니다.
 */
public record IntraConflict(String fieldName, String fieldText, String bodyText) {
}
