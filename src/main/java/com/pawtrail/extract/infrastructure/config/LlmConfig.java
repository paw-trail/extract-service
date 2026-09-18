package com.pawtrail.extract.infrastructure.config;

import com.pawtrail.extract.domain.provider.LlmProvider;
import com.pawtrail.extract.infrastructure.provider.external.LlmRetry;
import com.pawtrail.extract.infrastructure.provider.external.OllamaLlmProvider;
import com.pawtrail.extract.infrastructure.provider.external.OpenAiLlmProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

/**
 * 언어 모델 설정을 올리고 provider 에 맞는 구현 하나를 빈으로 만듭니다.
 *
 * <b>구현을 여기서 만드는 이유</b>
 * 구현에 @Component 를 붙이고 생성자에서 빌더에 제한시간 팩토리를 붙이면,
 * 테스트에서 MockRestServiceServer 가 빌더에 심어 둔 흉내 팩토리를 그 팩토리가 덮어써
 * 요청이 진짜로 나갑니다. 여기서 주소 · 제한시간을 붙인 RestClient 를 만들어 넘기면
 * 테스트는 흉내 서버에 묶은 RestClient 를 넘기면 됩니다.
 *
 * <b>빌더는 defaultRestClientBuilder 를 씁니다.</b>
 * 유레카를 거치지 않는 바깥 주소라 부하 분산 빌더는 맞지 않고,
 * 전역 제한시간(읽기 5초)은 모델 호출에 턱없이 짧아 제한시간은 여기서 따로 붙입니다.
 * 그 값을 늘리면 다른 호출까지 느슨해집니다.
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.extract.llm", name = "provider", havingValue = "ollama")
    public LlmProvider ollamaLlmProvider(@Qualifier("defaultRestClientBuilder") RestClient.Builder builder,
                                         LlmProperties properties,
                                         JsonMapper jsonMapper) {
        LlmProperties.Ollama ollama = properties.ollama();
        RestClient restClient = builder
                .baseUrl(ollama.baseUrl())
                .requestFactory(timeoutFactory(ollama.timeoutSeconds()))
                .build();
        return new OllamaLlmProvider(restClient, ollama, jsonMapper, retry(properties));
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.extract.llm", name = "provider", havingValue = "openai")
    public LlmProvider openAiLlmProvider(@Qualifier("defaultRestClientBuilder") RestClient.Builder builder,
                                         LlmProperties properties,
                                         JsonMapper jsonMapper) {
        LlmProperties.OpenAi openai = properties.openai();
        RestClient restClient = builder
                .baseUrl(openai.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openai.apiKey())
                .requestFactory(timeoutFactory(openai.timeoutSeconds()))
                .build();
        return new OpenAiLlmProvider(restClient, openai, jsonMapper, retry(properties));
    }

    private static LlmRetry retry(LlmProperties properties) {
        return new LlmRetry(properties.maxRetries(), properties.retryBackoffMs());
    }

    private static SimpleClientHttpRequestFactory timeoutFactory(long seconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(seconds));
        factory.setReadTimeout(Duration.ofSeconds(seconds));
        return factory;
    }
}
