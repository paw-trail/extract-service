package com.pawtrail.extract.domain.provider;

import com.pawtrail.extract.domain.exception.LlmDocumentException;
import com.pawtrail.extract.domain.exception.LlmUnavailableException;
import com.pawtrail.extract.domain.model.LlmAnswer;
import com.pawtrail.extract.domain.model.Segment;

import java.util.List;
import java.util.Optional;

/**
 * 원문 조각을 모델에 보내 조건과 근거 번호를 받아 옵니다.
 *
 * 구현은 둘입니다 — 로컬 Ollama 와 OpenAI. 설정 키 app.extract.llm.provider 로 하나만 뜹니다.
 * 프롬프트 · 응답 스키마 · 입력 글은 두 구현이 함께 쓰고 전송만 갈립니다.
 */
public interface LlmProvider {

    /**
     * @throws LlmUnavailableException 재시도해도 안 되거나 설정이 잘못돼 이번 실행을 더 할 수 없을 때
     * @throws LlmDocumentException    이 문서의 답을 쓸 수 없을 때 — 그 문서만 실패로 둠
     */
    LlmAnswer read(List<Segment> segments);

    /**
     * 같은 조각을 한 번 더 읽습니다. 두 번 읽도록 설정되지 않았으면 비어 있습니다.
     *
     * 두 읽기를 안전 쪽으로 합쳐 허용을 넓히는 틀림을 줄이려는 것입니다 (ReadingMerger).
     * 첫 읽기와 다른 설정(추론 강도)으로 읽어야 뜻이 있어 구현이 그 설정을 가집니다.
     * 실패의 뜻은 read 와 같습니다 — 문서 탓이면 그 문서만 실패, 부를 수 없으면 실행을 멈춤.
     */
    default Optional<LlmAnswer> readSecond(List<Segment> segments) {
        return Optional.empty();
    }

    /**
     * 추출 기록(extractedBy)에 남길 모델 이름입니다.
     */
    String modelName();

    /**
     * 추출 기록(promptVersion)에 남길 프롬프트 판입니다.
     */
    String promptVersion();
}
