package com.pawtrail.extract.application.service;

import com.pawtrail.extract.application.support.DocumentOutcome;
import com.pawtrail.extract.application.support.LlmReuse;
import com.pawtrail.extract.domain.enums.ExtractionMethod;
import com.pawtrail.extract.domain.enums.SourceType;
import com.pawtrail.extract.domain.exception.LlmDocumentException;
import com.pawtrail.extract.domain.exception.PayloadKeyMissingException;
import com.pawtrail.extract.domain.model.ConditionFields;
import com.pawtrail.extract.domain.model.LlmReading;
import com.pawtrail.extract.domain.model.MergedReading;
import com.pawtrail.extract.domain.model.PendingDocument;
import com.pawtrail.extract.domain.model.PolicyItem;
import com.pawtrail.extract.domain.model.RuleResult;
import com.pawtrail.extract.domain.model.SourceRaw;
import com.pawtrail.extract.domain.provider.PayloadReader;
import com.pawtrail.extract.domain.rule.ConditionMerger;
import com.pawtrail.extract.domain.rule.ConditionNormalizer;
import com.pawtrail.extract.domain.rule.ConditionRules;
import com.pawtrail.extract.domain.rule.PolicyItemCheck;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 원문 한 건을 policy 로 보낼 항목으로 만듭니다.
 *
 * <pre>
 * ① 원문 읽기     PayloadReader — 소스별 record 로 · 약속한 키가 없으면 실패
 * ② 규칙         ConditionRules — 정형 칸 · 모델에 넘길 문장 · 보내지 않을 원문 가리기
 * ③ 모델         LlmExtractor — 문장만 · 한 실행 안에서 같은 입력은 한 번
 * ④ 합치기       ConditionMerger — 같은 칸이 갈리면 비우고 소스 내 충돌
 * ⑤ 정규화       ConditionNormalizer — 서로 따라오는 칸 채우기
 * ⑥ 검사         PolicyItemCheck — policy 가 400 으로 막을 값이면 이 원문만 실패
 * </pre>
 *
 * <b>모델을 부를 수 없는 실패(LlmUnavailableException)는 잡지 않고 올려 보냅니다.</b>
 * 이 원문 탓이 아니므로 실패로 두면 안 되고, 실행이 멈춰 그 청크를 다시 가져가야 합니다.
 * 이 원문 탓인 실패(응답 잘림 · 스키마 어긋남 · 키 없음 · 검사 걸림)만 결과로 돌려줍니다.
 */
@Service
public class DocumentExtraction {

    private final Map<SourceType, PayloadReader> readers = new EnumMap<>(SourceType.class);
    private final LlmExtractor llmExtractor;

    public DocumentExtraction(List<PayloadReader> readers, LlmExtractor llmExtractor) {
        readers.forEach(reader -> this.readers.put(reader.source(), reader));
        this.llmExtractor = llmExtractor;
    }

    /**
     * @throws com.pawtrail.extract.domain.exception.LlmUnavailableException 모델을 부를 수 없을 때 — 실행을 멈춤
     */
    public DocumentOutcome extract(PendingDocument document, LlmReuse reuse) {
        PayloadReader reader = readers.get(document.source());
        if (reader == null) {
            return DocumentOutcome.failed("읽기가 없는 소스: " + document.source());
        }

        SourceRaw raw;
        try {
            raw = reader.read(document.payload());
        } catch (PayloadKeyMissingException e) {
            return DocumentOutcome.failed("원문에 약속한 키가 없음: " + e.block() + "." + e.key());
        }

        RuleResult rule = ConditionRules.extract(raw);
        switch (rule.outcome()) {
            case SKIP_DONE -> {
                return DocumentOutcome.skipped(rule.reason());
            }
            case FAILED -> {
                return DocumentOutcome.failed(rule.reason());
            }
            case EMPTY -> {
                // 뽑았으나 조건을 못 찾음 — 20칸이 빈 행으로 보내야 예전 조건이 남지 않음
                MergedReading empty = new MergedReading(ConditionFields.empty(), List.of(), List.of(),
                        ExtractionMethod.RULE);
                return DocumentOutcome.send(PolicyItem.of(document.placeId(), document.source(), empty));
            }
            default -> {
                return extractWithModel(document, rule, reuse);
            }
        }
    }

    private DocumentOutcome extractWithModel(PendingDocument document, RuleResult rule, LlmReuse reuse) {
        LlmReading llm;
        try {
            llm = llmExtractor.extract(rule.texts(), reuse);
        } catch (LlmDocumentException e) {
            return DocumentOutcome.failed("모델 답을 쓸 수 없음(" + e.reason() + "): " + e.getMessage());
        }

        MergedReading merged = ConditionNormalizer.normalize(
                ConditionMerger.merge(rule.fields(), rule.evidence(), llm));
        PolicyItem item = PolicyItem.of(document.placeId(), document.source(), merged);

        List<String> problems = PolicyItemCheck.check(item);
        if (!problems.isEmpty()) {
            return DocumentOutcome.failed("policy 가 받지 않을 값: " + String.join(" · ", problems));
        }
        return DocumentOutcome.send(item);
    }
}
