package com.pawtrail.extract.support;

import com.pawtrail.extract.domain.model.FieldNames;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 모델이 쓴 것 같은 답 JSON 을 만듭니다. 응답을 흉내 내는 테스트가 씁니다.
 *
 * 스키마대로 스무 칸을 모두 적고(값이 없으면 null) 근거를 뒤에 붙입니다.
 *
 * <pre>
 * ModelAnswers.answer()
 *         .value("sizeRule", "ALL").cite("sizeRule", 1)
 *         .json()
 * </pre>
 */
public final class ModelAnswers {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final Map<String, Object> fields = new LinkedHashMap<>();
    private final List<Map<String, Object>> evidence = new ArrayList<>();

    private ModelAnswers() {
        for (String name : FieldNames.ALL) {
            fields.put(name, null);
        }
    }

    public static ModelAnswers answer() {
        return new ModelAnswers();
    }

    public ModelAnswers value(String fieldName, Object value) {
        fields.put(fieldName, value);
        return this;
    }

    public ModelAnswers cite(String fieldName, Integer... segments) {
        Map<String, Object> citation = new LinkedHashMap<>();
        citation.put("fieldName", fieldName);
        citation.put("segments", List.of(segments));
        evidence.add(citation);
        return this;
    }

    public String json() {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("fields", fields);
        answer.put("evidence", evidence);
        return MAPPER.writeValueAsString(answer);
    }

    public static String toJson(Object value) {
        return MAPPER.writeValueAsString(value);
    }
}
