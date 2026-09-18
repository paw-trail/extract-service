package com.pawtrail.extract.application.dto.output;

import java.time.LocalDateTime;

/**
 * 트리거가 실행을 받은 결과입니다. 실행은 비동기로 돌고, 결과는 끝의 요약 로그 한 줄에 남습니다.
 *
 * @param startedAt 실행을 시작한 시각 — policy 의 추출 시각(extractedAt)이 되어 이 실행이 보낸 행을 묶음
 * @param limit     처리할 원문 수의 상한 — 비어 있으면 대기가 빌 때까지
 */
public record TriggerOutput(LocalDateTime startedAt, Integer limit) {
}
