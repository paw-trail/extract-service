package com.pawtrail.extract.domain.model;

/**
 * 처리 결과를 되돌려 쓴 결과입니다.
 *
 * <pre>
 * updated   상태를 바꾼 원문 수
 * skipped   가져간 사이에 내용이 바뀌어 대기로 남은 원문 수 — 오류가 아니며 다음 실행이 새 내용을 가져감
 * </pre>
 */
public record StatusResult(int updated, int skipped) {
}
