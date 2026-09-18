package com.pawtrail.extract.support;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * 테스트 자원의 원문 표본을 Map 으로 읽습니다.
 *
 * 표본은 2026.9.13 원문 덤프에서 유형별로 한 건씩 골라 payload 를 그대로 옮긴 것입니다.
 * 경로는 src/test/resources/raw/{소스}/{이름}.json 입니다.
 *
 * 실행할 때 원문은 ingest 의 원문 목록 응답에서 풀려 Map 으로 들어옵니다.
 * 여기서도 같은 모양으로 풀어 읽기에 넘깁니다.
 */
public final class RawFixtures {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private RawFixtures() {
    }

    public static Map<String, Object> load(String path) {
        try (InputStream in = RawFixtures.class.getResourceAsStream("/raw/" + path + ".json")) {
            if (in == null) {
                throw new IllegalArgumentException("표본이 없습니다: " + path);
            }
            return MAPPER.readValue(in, new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
