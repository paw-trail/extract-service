package com.pawtrail.extract.domain.model;

/**
 * 한국문화정보원 반려동물 동반 가능 문화시설 원문에서 규칙이 쓰는 여덟 칸입니다.
 *
 * <b>키 이름을 원문 그대로 씁니다.</b>
 * 실내와 실외의 키가 띄어쓰기로 갈려 있습니다 — "장소(실내) 여부" 는 괄호 뒤에 공백이 있고
 * "장소(실외)여부" 는 없습니다. 규칙적으로 맞춰 쓰면 한쪽이 조용히 비고,
 * 조건 칸의 빈 값은 "정보 없음" 으로 판정에 섞여 틀린 줄도 모르게 됩니다.
 * 그래서 키가 없으면 읽기가 실패하게 두었습니다.
 */
public record CultureRaw(
        String category3,
        String companion,
        String indoor,
        String outdoor,
        String petOnly,
        String size,
        String fee,
        String restriction
) implements SourceRaw {

    public static final String BLOCK = "list";
    public static final String KEY_CATEGORY3 = "카테고리3";
    public static final String KEY_COMPANION = "반려동물 동반 가능정보";
    public static final String KEY_INDOOR = "장소(실내) 여부";
    public static final String KEY_OUTDOOR = "장소(실외)여부";
    public static final String KEY_PET_ONLY = "반려동물 전용 정보";
    public static final String KEY_SIZE = "입장 가능 동물 크기";
    public static final String KEY_FEE = "애견 동반 추가 요금";
    public static final String KEY_RESTRICTION = "반려동물 제한사항";

    /**
     * list 블록이 없거나 비어 있는 원문입니다. 실물에는 없으나 petTour 블록과 같은 규칙으로 다룹니다.
     */
    public static CultureRaw withoutBlock() {
        return new CultureRaw(null, null, null, null, null, null, null, null);
    }
}
