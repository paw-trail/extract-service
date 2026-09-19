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
import com.pawtrail.extract.domain.rule.ReadingMerger;
import com.pawtrail.extract.domain.rule.Segmenter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 규칙 추출이 넘긴 원문 칸을 모델에게 읽혀 조건과 근거를 얻습니다.
 *
 * <pre>
 * ① 조각 나누기     Segmenter — 원문 칸 → 번호 붙은 조각
 * ② 재사용 확인     LlmReuse — 한 실행 안에서 같은 입력이면 앞의 결과
 * ③ 모델 호출      LlmProvider — Ollama 또는 OpenAI
 * ④ 근거 검사      CitationCheck — 근거 없는 값을 버리고 번호를 원문 근거로
 * ⑤ 한 번 더       두 번 읽도록 설정됐으면 같은 조각을 다른 추론 강도로 다시 읽고 ④ 를 거침
 * ⑥ 합치기        ReadingMerger — 두 읽기를 칸마다 안전 쪽으로
 * </pre>
 *
 * 실패는 그대로 올려 보냅니다. 받은 실행이 LlmUnavailableException 이면 멈추고,
 * LlmDocumentException 이면 그 문서만 실패로 둡니다.
 * 두 번째 읽기만 실패해도 같습니다 — 첫 읽기만으로 보내면 두 번 읽는 까닭(허용 쪽 틀림 줄이기)이
 * 그 문서에서만 조용히 빠지고, 어느 문서가 한 번만 읽혔는지 남지 않기 때문입니다.
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
        return reuse.readingFor(segments, () -> read(segments));
    }

    /**
     * 한 번 읽고, 두 번 읽도록 설정됐으면 한 번 더 읽어 합칩니다.
     *
     * 두 읽기 모두 근거 검사를 먼저 거칩니다. 합친 뒤에 검사하면 근거 없는 값이 좁은 쪽으로
     * 뽑혔다가 버려져, 다른 읽기의 멀쩡한 값까지 잃습니다.
     */
    private LlmReading read(List<Segment> segments) {
        LlmReading first = CitationCheck.check(llmProvider.read(segments), segments);
        Optional<LlmAnswer> again = llmProvider.readSecond(segments);
        if (again.isEmpty()) {
            return first;
        }
        return ReadingMerger.merge(first, CitationCheck.check(again.get(), segments));
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
