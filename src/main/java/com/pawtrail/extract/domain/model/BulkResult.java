package com.pawtrail.extract.domain.model;

/**
 * policy 가 한 묶음을 받은 결과입니다.
 *
 * <pre>
 * accepted   받아서 저장한 항목 수 — 보낸 수와 같아야 함
 * merged     다시 합친 장소 수 — 한 장소에 소스가 여럿 오면 한 번만 합치므로 accepted 보다 작을 수 있음
 * </pre>
 */
public record BulkResult(int accepted, int merged) {
}
