package com.pawtrail.extract.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 실행 루프의 설정입니다 (app.extract).
 *
 * 모델 호출 값(app.extract.llm)은 LlmProperties 가 따로 받습니다.
 * 이 record 는 llm 아래를 모르고, 그 자리는 바인딩이 조용히 건너뜁니다.
 *
 * 검증을 더할 때는 세 곳을 함께 봅니다.
 * <pre>
 * config 저장소의 extract-service.yml
 * src/test/resources/application.yml   설정 서버를 끄므로 값이 여기서만 옴
 * 이 클래스
 * </pre>
 *
 * @param chunkSize              ingest 에서 한 번에 가져와 처리하는 원문 수 — policy bulk 한 번의 항목 수도 됨
 * @param maxConsecutiveFailures 문서 탓 실패가 이만큼 연달아 나면 실행을 멈춤
 * @param policy                 policy 호출 값
 */
@Validated
@ConfigurationProperties(prefix = "app.extract")
public record ExtractProperties(

        // policy bulk 가 한 번에 500건까지 받음
        // 청크 하나가 곧 bulk 한 번이라 그 위로는 둘 수 없음
        @NotNull(message = "app.extract.chunk-size 가 필요합니다")
        @Positive(message = "app.extract.chunk-size 는 양수여야 합니다")
        @Max(value = 500, message = "app.extract.chunk-size 는 policy bulk 상한 500 을 넘을 수 없습니다")
        Integer chunkSize,

        @NotNull(message = "app.extract.max-consecutive-failures 가 필요합니다")
        @Positive(message = "app.extract.max-consecutive-failures 는 양수여야 합니다")
        Integer maxConsecutiveFailures,

        @NotNull(message = "app.extract.policy 가 필요합니다")
        @Valid
        Policy policy
) {

    /**
     * @param readTimeoutSeconds bulk 응답을 기다리는 시간 — 전역 읽기 5초와 따로 둠
     *                           policy 가 100곳을 잠그고 합치고 근거를 쓰는 동안 기다려야 함
     */
    public record Policy(
            @NotNull(message = "app.extract.policy.read-timeout-seconds 가 필요합니다")
            @Positive(message = "app.extract.policy.read-timeout-seconds 는 양수여야 합니다")
            Integer readTimeoutSeconds
    ) {
    }
}
