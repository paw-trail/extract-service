package com.pawtrail.extract.infrastructure.provider.internal.dto;

import com.pawtrail.extract.domain.model.StatusMark;

import java.util.List;

/**
 * ingest PATCH /internal/raw/status 의 요청입니다.
 *
 * 원문마다 가져올 때 받은 내용 해시를 함께 보냅니다.
 * 그사이 재수집이 내용을 바꿨으면 ingest 가 그 원문의 상태를 그대로 두고 skipped 로 셉니다.
 */
public record RawStatusRequest(List<StatusMark> done, List<StatusMark> failed) {
}
