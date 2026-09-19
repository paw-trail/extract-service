package com.pawtrail.extract.infrastructure.provider.internal.dto;

/**
 * policy POST /internal/policies/bulk 의 data 입니다.
 *
 * @param accepted 받아서 저장한 항목 수
 * @param merged   다시 합친 장소 수
 */
public record PolicyBulkResponse(int accepted, int merged) {
}
