package com.pawtrail.extract.domain.rule;

import com.pawtrail.extract.domain.model.SourceText;

import java.util.List;

/**
 * 규칙 셋이 함께 쓰는 문자열 처리입니다.
 */
final class RuleTexts {

    private RuleTexts() {
    }

    /**
     * 앞뒤 공백을 걷고, 남는 것이 없으면 null 을 돌려줍니다.
     */
    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    /**
     * 값이 있으면 넘길 원문으로 더합니다.
     */
    static void addText(List<SourceText> texts, String originField, String value) {
        String text = trimToNull(value);
        if (text != null) {
            texts.add(new SourceText(originField, text));
        }
    }
}
