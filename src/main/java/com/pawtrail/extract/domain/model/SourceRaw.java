package com.pawtrail.extract.domain.model;

/**
 * 소스별로 읽어 낸 원문입니다.
 *
 * 원문 JSON 을 읽는 일은 infrastructure 의 읽기가 맡고, 규칙은 이 record 만 봅니다.
 * 그래서 규칙이 원문 형식을 모르고, 규칙 테스트도 JSON 없이 record 를 만들어 돌립니다.
 *
 * 규칙이 소스마다 갈리므로 봉인해 둡니다. 새 소스를 더하면 규칙의 분기가
 * 컴파일 단계에서 빠진 자리를 알려 줍니다.
 */
public sealed interface SourceRaw permits PetTourRaw, GoCampingRaw, CultureRaw {
}
