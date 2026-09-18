package com.pawtrail.extract.domain.model;

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

    private FieldNames() {
    }
}
