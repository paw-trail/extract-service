package com.pawtrail.extract.domain.rule;

import com.pawtrail.extract.domain.model.ConditionFields;
import com.pawtrail.extract.domain.model.Evidence;
import com.pawtrail.extract.domain.model.FieldNames;
import com.pawtrail.extract.domain.model.LlmAnswer;
import com.pawtrail.extract.domain.model.LlmCitation;
import com.pawtrail.extract.domain.model.LlmReading;
import com.pawtrail.extract.domain.model.Segment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 모델 답의 근거를 검사하고, 조각 번호를 원문 근거로 바꿉니다.
 *
 * 스프링을 모르는 순수 계산입니다. 두 구현(Ollama · OpenAI)이 같은 규칙을 거치게 하려고
 * 전송 쪽이 아니라 여기에 둡니다.
 *
 * <b>근거가 없는 값은 쓰지 않습니다.</b>
 * <pre>
 * 값은 있는데 근거가 없는 칸       값을 버리고 셈 (droppedValues)
 * 비어 있는 칸을 가리키는 근거      무시하고 셈 (ignoredCitations)
 * 조건 칸이 아닌 이름의 근거        무시하고 셈 (ignoredCitations)
 * 범위 밖 조각 번호               그 번호만 버리고 셈 (droppedNumbers) — 남는 번호가 없으면 그 칸을 비움
 * 빈 목록                        정보 없음과 같이 봄 — 셈에 넣지 않음
 * </pre>
 * 시험에서 모델이 근거 없이 "예방접종 증명 필요" 를 지어낸 적이 있습니다.
 * 근거가 없는 값을 남기면 화면에 근거 없는 조건이 뜹니다.
 * 개수 제약을 스키마에 두지 않은 대신 이 검사가 그 몫을 맡습니다.
 *
 * 한 칸의 근거가 여러 줄이면 번호를 합치고, 같은 번호는 한 번만, 번호 순서대로 근거를 만듭니다.
 *
 * 스키마를 벗어난 답(알 수 없는 속성 · null 번호 · 스무 칸이 아닌 이름)은 두 구현이
 * 읽는 자리에서 먼저 그 문서를 실패로 둡니다. 여기서 조건 칸이 아닌 이름을 세는 것은
 * 다른 길로 들어온 답에도 같은 규칙이 걸리게 하려는 도메인 쪽 안전망입니다.
 */
public final class CitationCheck {

    private CitationCheck() {
    }

    public static LlmReading check(LlmAnswer answer, List<Segment> segments) {
        Map<Integer, Segment> byNumber = new LinkedHashMap<>();
        for (Segment segment : segments) {
            byNumber.put(segment.number(), segment);
        }
        ConditionFields fields = answer.fields();

        Map<String, TreeSet<Integer>> cited = new LinkedHashMap<>();
        int ignoredCitations = 0;
        int droppedNumbers = 0;
        for (LlmCitation citation : answer.citations()) {
            String name = citation.fieldName();
            if (name == null || !FieldNames.ALL.contains(name) || !hasValue(fields.get(name))) {
                ignoredCitations++;
                continue;
            }
            TreeSet<Integer> numbers = cited.computeIfAbsent(name, key -> new TreeSet<>());
            for (Integer number : citation.segments()) {
                if (number != null && byNumber.containsKey(number)) {
                    numbers.add(number);
                } else {
                    droppedNumbers++;
                }
            }
        }

        Set<String> cleared = new HashSet<>();
        int droppedValues = 0;
        List<Evidence> evidence = new ArrayList<>();
        for (String name : FieldNames.ALL) {
            Object value = fields.get(name);
            if (value == null) {
                continue;
            }
            if (!hasValue(value)) {
                cleared.add(name);
                continue;
            }
            Set<Integer> numbers = cited.getOrDefault(name, new TreeSet<>());
            if (numbers.isEmpty()) {
                cleared.add(name);
                droppedValues++;
                continue;
            }
            for (Integer number : numbers) {
                Segment segment = byNumber.get(number);
                evidence.add(Evidence.ofLlm(name, segment.originField(), segment.indexInField(), segment.text()));
            }
        }

        return new LlmReading(fields.without(cleared), evidence, droppedValues, ignoredCitations, droppedNumbers);
    }

    // 빈 목록은 "정보 없음" 과 같음 — 조건 칸에서 null 이 정보 없음이고 빈 목록은 쓰지 않음
    private static boolean hasValue(Object value) {
        return value != null && !(value instanceof List<?> list && list.isEmpty());
    }
}
