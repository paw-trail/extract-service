package com.pawtrail.extract.presentation.controller;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.extract.application.dto.output.TriggerOutput;
import com.pawtrail.extract.application.service.ExtractTriggerService;
import com.pawtrail.extract.presentation.request.ExtractTriggerRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 추출 실행을 부르는 내부 경로입니다.
 *
 * 게이트웨이가 /internal 을 라우팅하지 않으므로 바깥에서는 부를 수 없습니다.
 * 부르는 쪽은 Jenkins 잡과 사람이고, 로컬에서는 8089 로 바로 부릅니다.
 */
@RestController
@RequestMapping("/internal/extract")
public class InternalExtractController {

    private final ExtractTriggerService triggerService;

    public InternalExtractController(ExtractTriggerService triggerService) {
        this.triggerService = triggerService;
    }

    /**
     * 실행을 받고 바로 202 로 답합니다. 실행은 비동기로 돌고 결과는 끝의 요약 로그 한 줄에 남습니다.
     *
     * 이미 돌고 있으면 409 입니다.
     */
    @PostMapping("/trigger")
    public ResponseEntity<CommonApiResponse<TriggerOutput>> trigger(
            @Valid @RequestBody(required = false) ExtractTriggerRequest request) {
        Integer limit = request == null ? null : request.limit();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(CommonApiResponse.success(triggerService.start(limit)));
    }
}
