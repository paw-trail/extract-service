package com.pawtrail.extract.infrastructure.provider.internal.dto;

/**
 * ingest PATCH /internal/raw/status 의 data 입니다.
 *
 * @param updated 상태를 바꾼 원문 수
 * @param skipped 내용이 바뀌어 상태를 그대로 둔 원문 수
 */
public record RawStatusResponse(int updated, int skipped) {
}
