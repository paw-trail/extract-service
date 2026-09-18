package com.pawtrail.extract.infrastructure.provider.external;

import com.pawtrail.extract.domain.enums.SizeRule;
import com.pawtrail.extract.domain.exception.LlmDocumentException;
import com.pawtrail.extract.domain.model.LlmAnswer;
import com.pawtrail.extract.domain.model.LlmCitation;
import com.pawtrail.extract.support.ModelAnswers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmAnswerParserTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("스키마 모양의 답을 조건 칸과 근거 번호로 읽는다")
    void 읽기() {
        String content = ModelAnswers.answer()
                .value("maxWeightKg", 7.5).value("weightInclusive", true).value("maxCount", 3)
                .value("sizeRule", "SMALL_ONLY").value("requiredItems", List.of("배변봉투"))
                .cite("maxWeightKg", 1).cite("sizeRule", 1, 2)
                .json();

        LlmAnswer answer = LlmAnswerParser.parse(jsonMapper, content);

        assertThat(answer.fields().maxWeightKg()).isEqualByComparingTo(new BigDecimal("7.5"));
        assertThat(answer.fields().maxCount()).isEqualTo((short) 3);
        assertThat(answer.fields().sizeRule()).isEqualTo(SizeRule.SMALL_ONLY);
        assertThat(answer.fields().requiredItems()).containsExactly("배변봉투");
        assertThat(answer.fields().scope()).isNull();
        assertThat(answer.citations()).containsExactly(
                new LlmCitation("maxWeightKg", List.of(1)), new LlmCitation("sizeRule", List.of(1, 2)));
    }

    @Test
    @DisplayName("JSON 이 아니면 그 문서만 실패다")
    void JSON_아님() {
        assertThatThrownBy(() -> LlmAnswerParser.parse(jsonMapper, "{\"fields\":{\"scope\":"))
                .isInstanceOf(LlmDocumentException.class)
                .extracting(e -> ((LlmDocumentException) e).reason())
                .isEqualTo(LlmDocumentException.Reason.INVALID_ANSWER);
    }

    @Test
    @DisplayName("enum 에 없는 값이 오면 그 문서만 실패다")
    void 모르는_값() {
        String content = ModelAnswers.answer().value("sizeRule", "LARGE_ONLY").json();

        assertThatThrownBy(() -> LlmAnswerParser.parse(jsonMapper, content))
                .isInstanceOf(LlmDocumentException.class);
    }

    @Test
    @DisplayName("fields 나 evidence 가 빠지면 그 문서만 실패다")
    void 칸_빠짐() {
        String withoutEvidence = ModelAnswers.answer().json().replace(",\"evidence\":[]", "");

        assertInvalid(withoutEvidence);
    }

    @Test
    @DisplayName("스키마에 없는 속성이 맨 위에 있으면 그 문서만 실패다")
    void 모르는_속성_맨_위() {
        assertInvalid(ModelAnswers.answer().json().replaceFirst("\\{", "{\"note\":\"확인 필요\","));
    }

    @Test
    @DisplayName("fields 안에 스무 칸이 아닌 칸이 있으면 그 문서만 실패다")
    void 모르는_속성_칸() {
        assertInvalid(ModelAnswers.answer().value("parking", true).json());
    }

    @Test
    @DisplayName("스무 칸 중 하나가 빠지면 그 문서만 실패다 — 스키마는 스무 칸을 모두 요구한다")
    void 조건_칸_빠짐() {
        assertInvalid(ModelAnswers.answer().json().replace("\"scope\":null,", ""));
    }

    @Test
    @DisplayName("null 인 근거 줄이 있으면 그 문서만 실패다")
    void 근거_줄_null() {
        assertInvalid(ModelAnswers.answer().json().replace("\"evidence\":[]", "\"evidence\":[null]"));
    }

    @Test
    @DisplayName("조각 번호 목록이 null 이면 그 문서만 실패다")
    void 번호_목록_null() {
        assertInvalid(ModelAnswers.answer().value("leashRequired", true).json()
                .replace("\"evidence\":[]", "\"evidence\":[{\"fieldName\":\"leashRequired\",\"segments\":null}]"));
    }

    @Test
    @DisplayName("조각 번호에 null 이 섞이면 멀쩡한 번호만 추려 쓰지 않고 그 문서만 실패다")
    void 번호_null_섞임() {
        assertInvalid(ModelAnswers.answer().value("leashRequired", true).json()
                .replace("\"evidence\":[]", "\"evidence\":[{\"fieldName\":\"leashRequired\",\"segments\":[2,null]}]"));
    }

    @Test
    @DisplayName("근거의 칸 이름이 스무 칸이 아니면 그 문서만 실패다")
    void 모르는_칸_이름() {
        assertInvalid(ModelAnswers.answer().value("leashRequired", true).cite("parking", 1).json());
    }

    @Test
    @DisplayName("빈 답이면 그 문서만 실패다")
    void 빈_답() {
        assertInvalid("  ");
    }

    private void assertInvalid(String content) {
        assertThatThrownBy(() -> LlmAnswerParser.parse(jsonMapper, content))
                .isInstanceOf(LlmDocumentException.class)
                .extracting(e -> ((LlmDocumentException) e).reason())
                .isEqualTo(LlmDocumentException.Reason.INVALID_ANSWER);
    }
}
