package com.pawtrail.extract.eval;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 칸 하나의 모델 값을 정답과 견줘 경우를 가릅니다. 스프링을 모르는 순수 계산입니다.
 *
 * <pre>
 * MATCH       둘 다 같은 값                     맞음
 * DIFFERENT   둘 다 값인데 다름                  다름
 * FABRICATED  정답은 비었는데 모델이 채움          없는 걸 채움 — 지어내기 쪽
 * MISSED      정답은 값인데 모델이 비움           있는 걸 놓침
 * BOTH_EMPTY  둘 다 빔                         점수에서 뺌
 * </pre>
 *
 * 둘 다 빈 칸을 맞음에 넣지 않는 이유 — 스무 칸 대부분이 비어 있어 정확도가 90% 대로 부풀고 틀림이 묻힙니다.
 *
 * <b>같은 값의 뜻</b>
 * <pre>
 * 숫자     값으로 견줌 — 10 과 10.0 은 같음
 * enum    이름으로 견줌
 * 목록     순서만 무시하고 글자 그대로 견줌 · 항목 수가 같아야 함
 *         정답 항목이 표기 목록이면 그중 하나면 그 항목은 맞음
 * </pre>
 * 목록을 겹치기만 하면 맞음으로 보지 않는 이유 — 준비물에 입마개를 하나 더 넣은 차이가 묻힙니다.
 * 프롬프트를 다듬을 때 바로 그 차이를 잡으려 했습니다.
 */
final class EvalScorer {

    enum Verdict {
        MATCH,
        DIFFERENT,
        FABRICATED,
        MISSED,
        BOTH_EMPTY
    }

    private EvalScorer() {
    }

    static Verdict judge(Object gold, Object model) {
        boolean hasGold = hasValue(gold);
        boolean hasModel = hasValue(model);
        if (!hasGold && !hasModel) {
            return Verdict.BOTH_EMPTY;
        }
        if (!hasGold) {
            return Verdict.FABRICATED;
        }
        if (!hasModel) {
            return Verdict.MISSED;
        }
        return same(gold, model) ? Verdict.MATCH : Verdict.DIFFERENT;
    }

    static boolean same(Object gold, Object model) {
        if (gold instanceof List<?> goldItems) {
            return model instanceof List<?> modelItems && sameList(goldItems, modelItems);
        }
        if (gold instanceof Number goldNumber && model instanceof Number modelNumber) {
            return new BigDecimal(goldNumber.toString()).compareTo(new BigDecimal(modelNumber.toString())) == 0;
        }
        if (model instanceof Enum<?> modelEnum) {
            return modelEnum.name().equals(gold);
        }
        return gold.equals(model);
    }

    private static boolean sameList(List<?> goldItems, List<?> modelItems) {
        if (goldItems.size() != modelItems.size()) {
            return false;
        }
        List<String> remaining = new ArrayList<>();
        for (Object item : modelItems) {
            remaining.add(item.toString().strip());
        }
        for (Object goldItem : goldItems) {
            List<String> accepted = accepted(goldItem);
            String hit = null;
            for (String candidate : remaining) {
                if (accepted.contains(candidate)) {
                    hit = candidate;
                    break;
                }
            }
            if (hit == null) {
                return false;
            }
            remaining.remove(hit);
        }
        return remaining.isEmpty();
    }

    private static List<String> accepted(Object goldItem) {
        if (goldItem instanceof List<?> alternatives) {
            return alternatives.stream().map(value -> value.toString().strip()).toList();
        }
        return List.of(goldItem.toString().strip());
    }

    // 빈 목록은 정보 없음과 같음 — CitationCheck 가 모델의 빈 목록을 비우는 것과 맞춤
    private static boolean hasValue(Object value) {
        return value != null && !(value instanceof List<?> list && list.isEmpty());
    }
}
