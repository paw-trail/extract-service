package com.pawtrail.extract.presentation.request;

import jakarta.validation.constraints.Positive;

/**
 * 추출 실행 요청입니다. 본문 없이 보내도 됩니다.
 *
 * @param limit 처리할 원문 수의 상한 — 비우면 대기가 빌 때까지
 *              첫 전량 전에 몇 건만 돌려 볼 때 씀
 */
public record ExtractTriggerRequest(
        @Positive(message = "limit 은 양수여야 합니다.")
        Integer limit
) {
}
