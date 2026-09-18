package com.pawtrail.extract.infrastructure.provider.external;

import com.pawtrail.extract.domain.exception.LlmDocumentException;
import com.pawtrail.extract.domain.model.LlmAnswer;
import com.pawtrail.extract.domain.model.LlmCitation;
import com.pawtrail.extract.infrastructure.provider.external.dto.LlmAnswerJson;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 모델이 쓴 JSON 글을 도메인의 LlmAnswer 로 읽습니다. 두 구현이 함께 씁니다.
 *
 * 글이 비었거나 JSON 이 아니거나 스키마 모양과 다르면 그 문서만 실패로 둡니다.
 * 온도 0 이라 같은 입력은 같은 답을 받으므로 다시 불러도 결과가 같습니다.
 */
final class LlmAnswerParser {

    private LlmAnswerParser() {
    }

    static LlmAnswer parse(JsonMapper jsonMapper, String content) {
        if (content == null || content.isBlank()) {
            throw new LlmDocumentException(LlmDocumentException.Reason.INVALID_ANSWER, "모델 답이 비어 있습니다");
        }
        LlmAnswerJson json;
        try {
            json = jsonMapper.readValue(content, LlmAnswerJson.class);
        } catch (JacksonException e) {
            throw new LlmDocumentException(LlmDocumentException.Reason.INVALID_ANSWER,
                    "모델 답을 JSON 으로 읽지 못했습니다: " + e.getOriginalMessage(), e);
        }
        if (json == null || json.fields() == null || json.evidence() == null) {
            throw new LlmDocumentException(LlmDocumentException.Reason.INVALID_ANSWER,
                    "모델 답에 fields 나 evidence 가 없습니다");
        }
        List<LlmCitation> citations = new ArrayList<>();
        for (LlmAnswerJson.Citation citation : json.evidence()) {
            if (citation == null) {
                continue;
            }
            List<Integer> segments = citation.segments() == null ? List.of()
                    : citation.segments().stream().filter(Objects::nonNull).toList();
            citations.add(new LlmCitation(citation.fieldName(), segments));
        }
        return new LlmAnswer(json.fields(), citations);
    }
}
