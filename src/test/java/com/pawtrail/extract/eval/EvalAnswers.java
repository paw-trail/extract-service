package com.pawtrail.extract.eval;

import com.pawtrail.extract.domain.model.FieldNames;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 정답 파일(eval/{묶음}.json)과 표본 원문(eval/raw/{id}.json)을 읽습니다.
 *
 * <pre>
 * answers   평가 표본 100건 — 정확도를 재고 정답을 판정한 묶음
 * holdout   새 표본 40건 — 평가 표본을 보고 고친 프롬프트 판을 부풀지 않게 견주는 묶음
 *           평가 표본 · 프롬프트를 다듬을 때 쓴 시험 표본과 겹치는 입력은 하나도 없음
 * </pre>
 *
 * 원문은 2026.9.13 원문 덤프의 payload 를 그대로 옮긴 것이라 평가가 원문 읽기부터 실제 코드 길을 탑니다.
 * 두 묶음이 원문 폴더 하나를 함께 씁니다. 어떻게 뽑았는지는 각 정답 파일 머리의 about · rules 에 적혀 있습니다.
 */
final class EvalAnswers {

    static final String DEFAULT_SET = "answers";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private EvalAnswers() {
    }

    static List<EvalSample> load() {
        return load(DEFAULT_SET);
    }

    @SuppressWarnings("unchecked")
    static List<EvalSample> load(String set) {
        if (!set.matches("[a-z-]+")) {
            throw new IllegalArgumentException("표본 묶음 이름은 영문 소문자와 하이픈만 씁니다: " + set);
        }
        Map<String, Object> file = read("/eval/" + set + ".json");
        List<EvalSample> samples = new ArrayList<>();
        for (Object entry : (List<Object>) file.get("samples")) {
            Map<String, Object> sample = (Map<String, Object>) entry;
            Map<String, Object> fields = new LinkedHashMap<>();
            Map<String, Object> goldFields = (Map<String, Object>) sample.get("fields");
            for (String name : FieldNames.ALL) {
                if (!goldFields.containsKey(name)) {
                    throw new IllegalStateException("정답 " + sample.get("no") + " 에 " + name + " 칸이 없습니다");
                }
                fields.put(name, goldFields.get(name));
            }
            Map<String, List<Integer>> evidence = new LinkedHashMap<>();
            ((Map<String, Object>) sample.get("evidence")).forEach((name, numbers) ->
                    evidence.put(name, ((List<Object>) numbers).stream().map(n -> ((Number) n).intValue()).toList()));
            samples.add(new EvalSample(
                    ((Number) sample.get("no")).intValue(),
                    (String) sample.get("id"),
                    (String) sample.get("source"),
                    (String) sample.get("type"),
                    fields,
                    evidence));
        }
        return samples;
    }

    static Map<String, Object> payload(String id) {
        return read("/eval/raw/" + id + ".json");
    }

    private static Map<String, Object> read(String path) {
        try (InputStream in = EvalAnswers.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("평가 파일이 없습니다: " + path);
            }
            return MAPPER.readValue(in, new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
