package com.pawtrail.extract.domain.model;

import java.util.List;

/**
 * 조건 20칸의 이름입니다.
 *
 * 근거와 소스 내 충돌이 이 이름으로 칸을 가리킵니다.
 * policy 가 bulk 요청을 받을 때 이 이름이 조건 20칸 중 하나인지 검사하고
 * 아니면 청크 전체를 400 으로 돌려보내므로, 글자 하나만 달라도 적재가 멈춥니다.
 * 그래서 문자열을 곳곳에 적지 않고 여기 한 곳에만 둡니다.
 *
 * 값은 ConditionFields 의 칸 이름과 같고, policy FieldSpec 의 이름과도 같습니다.
 */
public final class FieldNames {

    public static final String SCOPE = "scope";
    public static final String GUIDE_DOG_ONLY = "guideDogOnly";
    public static final String PET_ONLY = "petOnly";
    public static final String INDOOR_ALLOWED = "indoorAllowed";
    public static final String OUTDOOR_ALLOWED = "outdoorAllowed";
    public static final String MAX_WEIGHT_KG = "maxWeightKg";
    public static final String WEIGHT_INCLUSIVE = "weightInclusive";
    public static final String MAX_COUNT = "maxCount";
    public static final String SIZE_RULE = "sizeRule";
    public static final String BREED_RULE = "breedRule";
    public static final String CARRIER_REQUIRED = "carrierRequired";
    public static final String LEASH_REQUIRED = "leashRequired";
    public static final String EXCLUDED_ZONES = "excludedZones";
    public static final String ALLOWED_ZONES_ONLY = "allowedZonesOnly";
    public static final String EXCLUDED_DAYS = "excludedDays";
    public static final String EXTRA_FEE_AMOUNT = "extraFeeAmount";
    public static final String EXTRA_FEE_UNIT = "extraFeeUnit";
    public static final String REQUIRED_ITEMS = "requiredItems";
    public static final String VACCINE_PROOF = "vaccineProof";
    public static final String ADVANCE_INQUIRY = "advanceInquiry";

    /**
     * 스무 칸을 정한 순서대로 담았습니다.
     *
     * 모델에 보내는 응답 스키마와 근거 검사가 이 순서로 칸을 돕니다.
     * 순서가 실행마다 같아야 같은 입력에 같은 요청이 나갑니다.
     */
    public static final List<String> ALL = List.of(
            SCOPE, GUIDE_DOG_ONLY, PET_ONLY, INDOOR_ALLOWED, OUTDOOR_ALLOWED,
            MAX_WEIGHT_KG, WEIGHT_INCLUSIVE, MAX_COUNT, SIZE_RULE, BREED_RULE,
            CARRIER_REQUIRED, LEASH_REQUIRED, EXCLUDED_ZONES, ALLOWED_ZONES_ONLY, EXCLUDED_DAYS,
            EXTRA_FEE_AMOUNT, EXTRA_FEE_UNIT, REQUIRED_ITEMS, VACCINE_PROOF, ADVANCE_INQUIRY);

    private FieldNames() {
    }
}
