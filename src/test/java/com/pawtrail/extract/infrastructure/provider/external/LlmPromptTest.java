package com.pawtrail.extract.infrastructure.provider.external;

import com.pawtrail.extract.domain.model.FieldNames;
import com.pawtrail.extract.domain.model.Segment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LlmPromptTest {

    @Test
    @DisplayName("입력 글은 \"원문 조각\" 한 줄 뒤에 [번호] (한국어 칸 이름) 조각이다")
    void 입력_글() {
        String message = LlmPrompt.userMessage(List.of(
                new Segment(1, "acmpyPsblCpam", null, "전 견종 동반 가능"),
                new Segment(2, "acmpyNeedMtr", 1, "목줄 착용"),
                new Segment(3, "반려동물 제한사항", null, "야외만 반려동물 동반 가능, 목줄")));

        assertThat(message).isEqualTo("""
                원문 조각
                [1] (동반 가능 동물) 전 견종 동반 가능
                [2] (필요사항) 목줄 착용
                [3] (반려동물 제한사항) 야외만 반려동물 동반 가능, 목줄""");
    }

    @Test
    @DisplayName("조각 안의 줄바꿈은 빈칸으로 바꿔 한 조각이 한 줄이 되게 한다")
    void 조각_안_줄바꿈() {
        String message = LlmPrompt.userMessage(List.of(new Segment(1, "acmpyPsblCpam", null, "소형견만\n동반 가능")));

        assertThat(message).endsWith("[1] (동반 가능 동물) 소형견만 동반 가능");
    }

    @Test
    @DisplayName("스키마는 조건 칸이 먼저 · 근거가 뒤 · 스무 칸 모두 필수이고 null 을 허용한다")
    @SuppressWarnings("unchecked")
    void 스키마() {
        Map<String, Object> schema = LlmPrompt.schema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> fields = (Map<String, Object>) properties.get("fields");
        Map<String, Object> fieldProperties = (Map<String, Object>) fields.get("properties");

        assertThat(properties.keySet()).containsExactly("fields", "evidence");
        assertThat(schema.get("required")).isEqualTo(List.of("fields", "evidence"));
        assertThat(fieldProperties.keySet()).containsExactlyElementsOf(FieldNames.ALL);
        assertThat(fields.get("required")).isEqualTo(FieldNames.ALL);
        assertThat(fields.get("additionalProperties")).isEqualTo(false);
        assertThat(fieldProperties.values()).allSatisfy(property -> {
            List<Object> anyOf = (List<Object>) ((Map<String, Object>) property).get("anyOf");
            assertThat(anyOf.get(0)).isEqualTo(Map.of("type", "null"));
        });
    }

    @Test
    @DisplayName("근거의 칸 이름은 스무 칸 중 하나로 묶고, 개수 제약은 두지 않는다")
    @SuppressWarnings("unchecked")
    void 근거_스키마() {
        Map<String, Object> properties = (Map<String, Object>) LlmPrompt.schema().get("properties");
        Map<String, Object> evidence = (Map<String, Object>) properties.get("evidence");
        Map<String, Object> item = (Map<String, Object>) evidence.get("items");
        Map<String, Object> itemProperties = (Map<String, Object>) item.get("properties");

        assertThat(((Map<String, Object>) itemProperties.get("fieldName")).get("enum")).isEqualTo(FieldNames.ALL);
        assertThat(evidence).doesNotContainKeys("minItems", "maxItems");
        assertThat(item.get("additionalProperties")).isEqualTo(false);
    }

    @Test
    @DisplayName("프롬프트는 v2 다 — v1 의 규칙 아홉과 예시를 그대로 두고 칸의 뜻에 일반 규칙을 더한 판")
    void 프롬프트_판() {
        assertThat(LlmPrompt.VERSION).isEqualTo("v2");
        assertThat(LlmPrompt.SYSTEM)
                .startsWith("반려동물 동반 조건을 원문 조각에서 뽑아 JSON 으로 답한다.")
                .contains("9. 근거는 조각 번호로만 답하고 문장을 새로 쓰지 않는다.")
                .contains("\"전 견종 동반 가능\" 은 ALL 이다.")
                .contains("맹견을 따로 말하지 않은 \"전 견종 동반 가능\" 은 NONE 이다.")
                .contains("돌려받는 예치금이나 보증금은 추가 요금이 아니므로 적지 않는다")
                .endsWith("{\"fieldName\":\"excludedZones\",\"segments\":[4]}]}");
    }
}
