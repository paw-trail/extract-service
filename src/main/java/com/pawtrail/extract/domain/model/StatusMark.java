package com.pawtrail.extract.domain.model;

import java.util.UUID;

/**
 * 처리 결과를 되돌려 쓸 원문 하나입니다. ingest 상태 갱신 요청의 한 줄과 모양이 같습니다.
 *
 * <pre>
 * id           원문 식별자
 * contentHash  가져올 때 받은 내용 해시 — 그사이 재수집이 내용을 바꿨으면 ingest 가 상태를 그대로 둠
 * </pre>
 */
public record StatusMark(UUID id, String contentHash) {
}
