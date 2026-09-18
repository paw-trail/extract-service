package com.pawtrail.extract.eval;

import com.pawtrail.extract.domain.exception.LlmDocumentException;
import com.pawtrail.extract.domain.model.Evidence;
import com.pawtrail.extract.domain.model.FieldNames;
import com.pawtrail.extract.domain.model.LlmReading;
import com.pawtrail.extract.domain.model.Segment;
import com.pawtrail.extract.eval.EvalScorer.Verdict;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 표본마다의 채점을 모읍니다 — 칸별 경우 수 · 근거 적중 · 문서 탓 실패 · 갈린 칸.
 *
 * 근거 적중은 값이 맞은 칸에서만 셉니다. 모델이 댄 조각 번호가 정답 조각 번호와 하나라도 겹치면 적중입니다.
 * 값이 틀린 칸의 근거는 갈린 칸 목록에 함께 실어 판정할 때 봅니다.
 *
 * 문서 탓 실패로 끝난 표본은 칸별 표에 넣지 않고 까닭별로 따로 셉니다.
 */
final class EvalScore {

    record Disagreement(EvalSample sample, String field, Verdict verdict, Object gold, Object model,
                        List<Integer> goldSegments, List<Integer> modelSegments) {
    }

    record Failure(EvalSample sample, LlmDocumentException.Reason reason, String message) {
    }

    record SampleResult(EvalSample sample, List<Segment> segments, LlmReading reading,
                        Map<String, List<Integer>> modelSegments, Failure failure) {
    }

    private final Map<String, EnumMap<Verdict, Integer>> byField = new LinkedHashMap<>();
    private final List<Disagreement> disagreements = new ArrayList<>();
    private final List<Failure> failures = new ArrayList<>();
    private final List<SampleResult> results = new ArrayList<>();
    private int evidenceChecked;
    private int evidenceHit;
    private int droppedValues;
    private int ignoredCitations;
    private int droppedNumbers;

    EvalScore() {
        for (String name : FieldNames.ALL) {
            byField.put(name, new EnumMap<>(Verdict.class));
        }
    }

    void add(EvalSample sample, List<Segment> segments, LlmReading reading) {
        Map<String, List<Integer>> modelSegments = numbers(reading.evidence(), segments);
        for (String name : FieldNames.ALL) {
            Object gold = sample.fields().get(name);
            Object model = reading.fields().get(name);
            Verdict verdict = EvalScorer.judge(gold, model);
            byField.get(name).merge(verdict, 1, Integer::sum);
            List<Integer> goldSegments = sample.evidence().getOrDefault(name, List.of());
            List<Integer> cited = modelSegments.getOrDefault(name, List.of());
            if (verdict == Verdict.MATCH) {
                evidenceChecked++;
                if (cited.stream().anyMatch(goldSegments::contains)) {
                    evidenceHit++;
                }
            } else if (verdict != Verdict.BOTH_EMPTY) {
                disagreements.add(new Disagreement(sample, name, verdict, gold, model, goldSegments, cited));
            }
        }
        droppedValues += reading.droppedValues();
        ignoredCitations += reading.ignoredCitations();
        droppedNumbers += reading.droppedNumbers();
        results.add(new SampleResult(sample, segments, reading, modelSegments, null));
    }

    void addFailure(EvalSample sample, List<Segment> segments, LlmDocumentException exception) {
        Failure failure = new Failure(sample, exception.reason(), exception.getMessage());
        failures.add(failure);
        results.add(new SampleResult(sample, segments, null, Map.of(), failure));
    }

    /**
     * 근거(원문 키 · 칸 안 순서 · 조각 글)를 문서 전체의 조각 번호로 되돌립니다.
     */
    static Map<String, List<Integer>> numbers(List<Evidence> evidence, List<Segment> segments) {
        Map<String, List<Integer>> numbers = new LinkedHashMap<>();
        for (Evidence item : evidence) {
            for (Segment segment : segments) {
                if (segment.originField().equals(item.originField())
                        && Objects.equals(segment.indexInField(), item.segmentIndex())
                        && segment.text().equals(item.segmentText())) {
                    numbers.computeIfAbsent(item.fieldName(), key -> new ArrayList<>()).add(segment.number());
                    break;
                }
            }
        }
        return numbers;
    }

    int count(String field, Verdict verdict) {
        return byField.get(field).getOrDefault(verdict, 0);
    }

    int total(Verdict verdict) {
        return byField.values().stream().mapToInt(counts -> counts.getOrDefault(verdict, 0)).sum();
    }

    int scored() {
        return results.size() - failures.size();
    }

    int evidenceChecked() {
        return evidenceChecked;
    }

    int evidenceHit() {
        return evidenceHit;
    }

    int droppedValues() {
        return droppedValues;
    }

    int ignoredCitations() {
        return ignoredCitations;
    }

    int droppedNumbers() {
        return droppedNumbers;
    }

    List<Disagreement> disagreements() {
        return disagreements;
    }

    List<Failure> failures() {
        return failures;
    }

    List<SampleResult> results() {
        return results;
    }
}
