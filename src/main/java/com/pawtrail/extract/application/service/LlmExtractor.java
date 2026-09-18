package com.pawtrail.extract.application.service;

import com.pawtrail.extract.application.support.LlmReuse;
import com.pawtrail.extract.domain.exception.LlmDocumentException;
import com.pawtrail.extract.domain.exception.LlmUnavailableException;
import com.pawtrail.extract.domain.model.LlmAnswer;
import com.pawtrail.extract.domain.model.LlmReading;
import com.pawtrail.extract.domain.model.Segment;
import com.pawtrail.extract.domain.model.SourceText;
import com.pawtrail.extract.domain.provider.LlmProvider;
import com.pawtrail.extract.domain.rule.CitationCheck;
import com.pawtrail.extract.domain.rule.Segmenter;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 규칙 추출이 넘긴 원문 칸을 모델에게 읽혀 조건과 근거를 얻습니다.
 *
 * <pre>
 * ① 조각 나누기     Segmenter — 원문 칸 → 번호 붙은 조각
 * ② 재사용 확인     LlmReuse — 한 실행 안에서 같은 입력이면 앞의 답
 * ③ 모델 호출      LlmProvider — Ollama 또는 OpenAI
 * ④ 근거 검사      CitationCheck — 근거 없는 값을 버리고 번호를 원문 근거로
 * </pre>
 *
 * 실패는 그대로 올려 보냅니다. 받은 실행이 LlmUnavailableException 이면 멈추고,
 * LlmDocumentException 이면 그 문서만 실패로 둡니다.
 * 규칙 결과와 합치는 일과 소스 내 충돌은 실행 쪽이 맡습니다.
 */
@Service
public class LlmExtractor {

    private final LlmProvider llmProvider;

    public LlmExtractor(LlmProvider llmProvider) {
        this.llmProvider = llmProvider;
    }

    /**
     * @throws LlmUnavailableException 모델을 부를 수 없어 실행을 멈춰야 할 때
     * @throws LlmDocumentException    이 문서의 답을 쓸 수 없을 때
     */
    public LlmReading extract(List<SourceText> texts, LlmReuse reuse) {
        List<Segment> segments = Segmenter.split(texts);
        if (segments.isEmpty()) {
            return LlmReading.empty();
        }
        LlmAnswer answer = reuse.answerFor(segments, () -> llmProvider.read(segments));
        return CitationCheck.check(answer, segments);
    }

    /**
     * 추출 기록(extractedBy)에 남길 모델 이름입니다.
     */
    public String modelName() {
        return llmProvider.modelName();
    }

    /**
     * 추출 기록(promptVersion)에 남길 프롬프트 판입니다.
     */
    public String promptVersion() {
        return llmProvider.promptVersion();
    }
}
