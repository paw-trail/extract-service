package com.pawtrail.extract.infrastructure.provider.external;

import com.pawtrail.extract.domain.exception.LlmDocumentException;
import com.pawtrail.extract.domain.model.FieldNames;
import com.pawtrail.extract.domain.model.LlmAnswer;
import com.pawtrail.extract.domain.model.LlmCitation;
import com.pawtrail.extract.infrastructure.provider.external.dto.LlmAnswerJson;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 모델이 쓴 JSON 글을 도메인의 LlmAnswer 로 읽습니다. 두 구현이 함께 씁니다.
 *
 * <b>응답 스키마를 벗어난 답은 믿지 않고 그 문서만 실패로 둡니다.</b>
 * <pre>
 * 글이 비었거나 JSON 이 아님
 * 스키마에 없는 속성이 있음              맨 위 · fields 안 · 근거 줄 안 어디든
 * 스키마가 요구한 속성이 빠짐             스무 칸 중 하나라도 · fieldName · segments
 * 근거 줄 · 조각 번호 목록 · 번호가 null
 * 근거의 칸 이름이 스무 칸이 아님
 * enum 에 없는 값
 * </pre>
 * 구조화 출력(Ollama format · OpenAI strict)이 스키마를 지키게 하므로 정상이면 여기 걸리지 않습니다.
 * 걸린다면 모델이 스키마를 따르지 않은 것이라, 일부만 추려 쓰면 그 답이 멀쩡한 답처럼
 * 기록되고 한 실행 재사용에도 담깁니다.
 *
 * 스키마 안에서 뜻이 어긋난 것(값은 있는데 근거가 없음 · 범위 밖 조각 번호)은
 * 여기가 아니라 CitationCheck 가 칸 단위로 거르고 셉니다.
 *
 * <b>알 수 없는 속성은 이 읽기에서만 켜서 거부합니다.</b>
 * Jackson 3 는 FAIL_ON_UNKNOWN_PROPERTIES 기본값이 false 라 DTO 에서 애너테이션을 지우는 것만으로는
 * 알 수 없는 속성이 조용히 무시됩니다. 빠진 속성도 FAIL_ON_MISSING_CREATOR_PROPERTIES 로 막습니다.
 */
final class LlmAnswerParser {

    private LlmAnswerParser() {
    }

    static LlmAnswer parse(JsonMapper jsonMapper, String content) {
        if (content == null || content.isBlank()) {
            throw invalid("모델 답이 비어 있습니다", null);
        }
        LlmAnswerJson json;
        try {
            ObjectReader reader = jsonMapper.readerFor(LlmAnswerJson.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
            json = reader.readValue(content);
        } catch (JacksonException e) {
            throw invalid("모델 답이 응답 스키마와 다릅니다: " + e.getOriginalMessage(), e);
        }
        if (json == null || json.fields() == null || json.evidence() == null) {
            throw invalid("모델 답의 fields 나 evidence 가 비어 있습니다", null);
        }
        List<LlmCitation> citations = new ArrayList<>();
        for (LlmAnswerJson.Citation citation : json.evidence()) {
            if (citation == null) {
                throw invalid("모델 답에 null 인 근거 줄이 있습니다", null);
            }
            if (citation.fieldName() == null || !FieldNames.ALL.contains(citation.fieldName())) {
                throw invalid("근거의 칸 이름이 조건 스무 칸이 아닙니다: " + citation.fieldName(), null);
            }
            if (citation.segments() == null || citation.segments().contains(null)) {
                throw invalid("근거의 조각 번호가 null 입니다: " + citation.fieldName(), null);
            }
            citations.add(new LlmCitation(citation.fieldName(), citation.segments()));
        }
        return new LlmAnswer(json.fields(), citations);
    }

    private static LlmDocumentException invalid(String message, Throwable cause) {
        return new LlmDocumentException(LlmDocumentException.Reason.INVALID_ANSWER, message, cause);
    }
}
