package com.pawtrail.extract.infrastructure.config;

import com.pawtrail.extract.domain.provider.PolicyProvider;
import com.pawtrail.extract.domain.provider.RawDocumentProvider;
import com.pawtrail.extract.infrastructure.provider.internal.IngestRawClient;
import com.pawtrail.extract.infrastructure.provider.internal.PolicyBulkClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 실행 루프 설정을 올리고 ingest · policy 를 부르는 두 구현을 빈으로 만듭니다.
 *
 * <b>구현을 여기서 만드는 이유는 LlmConfig 와 같습니다.</b>
 * 테스트가 흉내 서버에 묶은 RestClient 를 구현에 바로 넘길 수 있게, 주소와 제한시간은 여기서 붙입니다.
 *
 * <b>빌더는 internalRestClientBuilder 를 씁니다.</b>
 * lb:// 주소를 유레카로 풀고 인증 헤더를 붙이는 빌더입니다.
 * 한정자를 빠뜨리면 아무것도 얹히지 않은 기본 빌더가 조용히 주입되어, 기동이 아니라
 * 호출하는 순간 lb:// 를 못 풀고 실패합니다. 공통 모듈이 빌더를 부를 때마다 새로 만들어 주므로
 * 두 빈이 같은 빌더를 나눠 쓰다 주소가 덮이는 일은 없습니다.
 *
 * <b>ingest 호출은 요청 팩터리를 바꾸지 않습니다.</b>
 * 상태 갱신이 PATCH 인데 SimpleClientHttpRequestFactory 는 PATCH 를 보내지 못합니다.
 * 공통 빌더의 기본 팩터리(JDK HttpClient)와 전역 제한시간을 그대로 씁니다.
 * 원문 100건을 읽거나 상태 100건을 쓰는 데는 전역 읽기 5초로 충분합니다.
 *
 * <b>policy 호출만 읽기를 늘립니다.</b>
 * bulk 는 100곳을 잠그고 다시 합치느라 전역 5초를 넘길 수 있습니다. POST 하나뿐이라
 * ingest 의 place 호출과 같은 방식(SimpleClientHttpRequestFactory)으로 읽기만 늘립니다.
 */
@Configuration
@EnableConfigurationProperties(ExtractProperties.class)
public class ExtractConfig {

    private static final String INGEST_URL = "lb://ingest-service";
    private static final String POLICY_URL = "lb://policy-service";

    // 연결을 맺기까지 기다리는 시간 — 공통 설정과 같은 값
    // 요청 팩터리를 갈아 끼우면 그쪽 값이 통째로 빠져 여기서 다시 세움
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    @Bean
    public RawDocumentProvider ingestRawClient(
            @Qualifier("internalRestClientBuilder") RestClient.Builder builder) {
        return new IngestRawClient(builder.baseUrl(INGEST_URL).build());
    }

    @Bean
    public PolicyProvider policyBulkClient(
            @Qualifier("internalRestClientBuilder") RestClient.Builder builder,
            ExtractProperties properties) {
        RestClient restClient = builder
                .baseUrl(POLICY_URL)
                .requestFactory(timeoutFactory(properties.policy().readTimeoutSeconds()))
                .build();
        return new PolicyBulkClient(restClient);
    }

    private static SimpleClientHttpRequestFactory timeoutFactory(long readTimeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return factory;
    }
}
