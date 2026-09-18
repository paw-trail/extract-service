package com.pawtrail.extract.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 고캠핑 원문에서 규칙이 쓰는 칸입니다.
 *
 * <pre>
 * animalCmgCl   반려동물 출입      규칙이 읽음 — 불가능 · 가능 · 가능(소형견)
 * texts         글 칸 열세 개      반려 낱말이 든 칸만 LLM 에 넘김
 * </pre>
 *
 * 글 칸은 TEXT_KEYS 순서대로 담습니다. 넘길 원문의 순서가 곧 조각 번호의 순서가 되므로
 * 실행할 때마다 같은 순서여야 같은 입력이 같은 답을 받습니다.
 */
public record GoCampingRaw(
        String animalCmgCl,
        Map<String, String> texts
) implements SourceRaw {

    public static final String BLOCK = "list";
    public static final String KEY_ANIMAL = "animalCmgCl";

    /**
     * 반려 얘기가 나올 수 있는 글 칸입니다.
     *
     * 실측에서 반려 낱말이 걸린 칸은 소개 · 한 줄 소개 · 이름 · 툴팁 · 부대시설 기타 · 특징 순으로 많았고
     * 나머지 칸은 몇 건씩이었습니다.
     */
    public static final List<String> TEXT_KEYS = List.of(
            "facltNm", "lineIntro", "intro", "featureNm", "tooltip",
            "sbrsEtc", "posblFcltyEtc", "exprnProgrm", "clturEvent", "direction",
            "themaEnvrnCl", "glampInnerFclty", "caravInnerFclty");

    public GoCampingRaw {
        texts = Collections.unmodifiableMap(new LinkedHashMap<>(texts));
    }

    /**
     * list 블록이 없거나 비어 있는 원문입니다. 실물에는 없으나 petTour 블록과 같은 규칙으로 다룹니다.
     */
    public static GoCampingRaw withoutBlock() {
        return new GoCampingRaw(null, Map.of());
    }
}
