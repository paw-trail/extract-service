package com.pawtrail.extract.domain.model;

/**
 * 한국관광공사 반려동물 동반여행 원문에서 규칙이 쓰는 다섯 칸입니다.
 *
 * 원문의 petTour 블록에 들어 있습니다. 키 이름은 원문 그대로이며
 * 근거의 originField 도 이 이름을 씁니다.
 *
 * <pre>
 * acmpyTypeCd        동반 구분          규칙이 읽음 — 전구역 · 일부구역
 * acmpyPsblCpam      동반 가능 동물      정확히 "불가" 면 규칙이 읽고, 그 밖은 LLM 에 넘김
 * acmpyNeedMtr       필요사항           LLM 에 넘김
 * etcAcmpyInfo       기타 동반 정보      LLM 에 넘김
 * relaAcdntRiskMtr   사고 대비          LLM 에 넘김
 * </pre>
 *
 * 필요사항은 목줄 · 입마개처럼 고정된 낱말을 쉼표로 이은 칸이지만 규칙으로 읽지 않습니다.
 * 입마개 61건 중 47건이 다른 칸에서 맹견 얘기를 하고 있어, 낱말만 보면 전 견종의 준비물로 읽힙니다.
 * 네 칸을 함께 읽어야 뜻이 맞습니다.
 */
public record PetTourRaw(
        String acmpyTypeCd,
        String acmpyPsblCpam,
        String acmpyNeedMtr,
        String etcAcmpyInfo,
        String relaAcdntRiskMtr
) implements SourceRaw {

    public static final String BLOCK = "petTour";
    public static final String KEY_TYPE = "acmpyTypeCd";
    public static final String KEY_POSSIBLE = "acmpyPsblCpam";
    public static final String KEY_NEED = "acmpyNeedMtr";
    public static final String KEY_ETC = "etcAcmpyInfo";
    public static final String KEY_RISK = "relaAcdntRiskMtr";

    /**
     * petTour 블록이 없거나 비어 있는 원문입니다.
     *
     * 실물에서 7건이 petTour 를 {} 로 싣고 옵니다. 조건을 말하는 칸이 하나도 없으므로 빈 문서가 됩니다.
     * 블록에 키가 있는데 약속한 키 중 하나가 빠진 것과는 다르게 다룹니다 — 그쪽은 실패로 둡니다.
     */
    public static PetTourRaw withoutBlock() {
        return new PetTourRaw(null, null, null, null, null);
    }
}
