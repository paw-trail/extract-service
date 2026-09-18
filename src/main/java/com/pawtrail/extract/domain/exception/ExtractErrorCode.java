package com.pawtrail.extract.domain.exception;

import com.pawtrail.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 이 서비스의 도메인 에러 코드입니다.
 *
 * 공통 코드는 CommonErrorCode 에 있고 도메인 개념은 여기에 둡니다.
 * getCode 는 반드시 name 을 그대로 반환합니다. 상수 이름이 곧 응답의 code 값이자 API 계약입니다.
 *
 * 실행 중에 나는 실패(모델 · ingest · policy)는 여기 없습니다.
 * 트리거는 202 로 먼저 답하고 실행은 비동기로 돌아, 그 실패를 받을 HTTP 응답이 없습니다.
 * 실행 끝의 요약 로그 한 줄이 그 자리를 맡습니다.
 */
public enum ExtractErrorCode implements ErrorCode {

    // 이미 도는 실행이 있음
    //
    // 두 실행이 같은 대기열 첫 쪽을 함께 가져가면 같은 원문을 두 번 뽑고
    // 같은 장소를 두 번 보내 policy 판이 괜히 오름
    EXTRACT_ALREADY_RUNNING(HttpStatus.CONFLICT, "이미 실행 중인 추출이 있습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ExtractErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getMessage() {
        return message;
    }
}
