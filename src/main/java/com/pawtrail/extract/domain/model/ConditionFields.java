package com.pawtrail.extract.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pawtrail.extract.domain.enums.BreedRule;
import com.pawtrail.extract.domain.enums.ExtraFeeUnit;
import com.pawtrail.extract.domain.enums.Scope;
import com.pawtrail.extract.domain.enums.SizeRule;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 원문 한 건에서 뽑은 동반 조건 20칸입니다.
 *
 * 칸 이름과 타입은 policy bulk 요청의 조건 칸(PolicyFieldsRequest)과 같습니다.
 * 이 값이 그대로 JSON 으로 실려 나가므로 이름이 어긋나면 policy 가 그 칸을 모르고 버립니다.
 *
 * <b>null 은 "정보 없음" 이고 false 와 다릅니다.</b>
 * vaccineProof 가 null 이면 원문이 말하지 않은 것이고, false 면 "요구하지 않는다" 고 말한 것입니다.
 * 판정이 둘을 다르게 다루므로 원문이 말하지 않은 칸은 반드시 비워 둡니다.
 *
 * 목록 칸도 같습니다. 빈 목록이 아니라 null 이 "정보 없음" 입니다.
 *
 * <b>빌더는 Lombok 이 만듭니다.</b>
 * 규칙은 원문을 읽으며 채울 수 있는 칸만 채우므로 빌더로 하나씩 채웁니다.
 * 손으로 쓰면 스무 칸이 필드 · 설정 메서드 · 생성자 인자에 세 번 되풀이되고,
 * 같은 타입이 이어지는 칸(실내 · 실외 같은 Boolean)은 생성자 인자 순서가 뒤바뀌어도
 * 컴파일이 통과해 값이 조용히 엇갈립니다. policy 의 PolicyFields 도 Lombok 빌더를 씁니다.
 * 빌더 이름을 Builder 로 둔 것은 규칙이 ConditionFields.Builder 로 받아 쓰기 때문입니다.
 */
@Builder(builderClassName = "Builder")
public record ConditionFields(
        Scope scope,
        Boolean guideDogOnly,
        Boolean petOnly,
        Boolean indoorAllowed,
        Boolean outdoorAllowed,
        BigDecimal maxWeightKg,
        Boolean weightInclusive,
        Short maxCount,
        SizeRule sizeRule,
        BreedRule breedRule,
        Boolean carrierRequired,
        Boolean leashRequired,
        List<String> excludedZones,
        List<String> allowedZonesOnly,
        List<String> excludedDays,
        Integer extraFeeAmount,
        ExtraFeeUnit extraFeeUnit,
        List<String> requiredItems,
        Boolean vaccineProof,
        Boolean advanceInquiry
) {

    public ConditionFields {
        excludedZones = copyOrNull(excludedZones);
        allowedZonesOnly = copyOrNull(allowedZonesOnly);
        excludedDays = copyOrNull(excludedDays);
        requiredItems = copyOrNull(requiredItems);
    }

    public static ConditionFields empty() {
        return builder().build();
    }

    /**
     * 20칸이 전부 비었는지 봅니다.
     *
     * 규칙 추출이 이것과 넘길 원문의 유무를 함께 보고 빈 문서를 가립니다.
     * JSON 으로 실려 나갈 칸이 아니라 계산용이라 직렬화에서 뺍니다.
     */
    @JsonIgnore
    public boolean isEmpty() {
        return Stream.of(scope, guideDogOnly, petOnly, indoorAllowed, outdoorAllowed,
                        maxWeightKg, weightInclusive, maxCount, sizeRule, breedRule,
                        carrierRequired, leashRequired, excludedZones, allowedZonesOnly, excludedDays,
                        extraFeeAmount, extraFeeUnit, requiredItems, vaccineProof, advanceInquiry)
                .allMatch(Objects::isNull);
    }

    /**
     * 칸 이름으로 값을 꺼냅니다.
     *
     * 근거 검사가 모델이 댄 칸 이름으로 값이 있는지 볼 때 씁니다.
     *
     * @throws IllegalArgumentException 조건 스무 칸의 이름이 아닐 때
     */
    public Object get(String fieldName) {
        return switch (fieldName) {
            case FieldNames.SCOPE -> scope;
            case FieldNames.GUIDE_DOG_ONLY -> guideDogOnly;
            case FieldNames.PET_ONLY -> petOnly;
            case FieldNames.INDOOR_ALLOWED -> indoorAllowed;
            case FieldNames.OUTDOOR_ALLOWED -> outdoorAllowed;
            case FieldNames.MAX_WEIGHT_KG -> maxWeightKg;
            case FieldNames.WEIGHT_INCLUSIVE -> weightInclusive;
            case FieldNames.MAX_COUNT -> maxCount;
            case FieldNames.SIZE_RULE -> sizeRule;
            case FieldNames.BREED_RULE -> breedRule;
            case FieldNames.CARRIER_REQUIRED -> carrierRequired;
            case FieldNames.LEASH_REQUIRED -> leashRequired;
            case FieldNames.EXCLUDED_ZONES -> excludedZones;
            case FieldNames.ALLOWED_ZONES_ONLY -> allowedZonesOnly;
            case FieldNames.EXCLUDED_DAYS -> excludedDays;
            case FieldNames.EXTRA_FEE_AMOUNT -> extraFeeAmount;
            case FieldNames.EXTRA_FEE_UNIT -> extraFeeUnit;
            case FieldNames.REQUIRED_ITEMS -> requiredItems;
            case FieldNames.VACCINE_PROOF -> vaccineProof;
            case FieldNames.ADVANCE_INQUIRY -> advanceInquiry;
            default -> throw new IllegalArgumentException("조건 칸이 아닙니다: " + fieldName);
        };
    }

    /**
     * 주어진 칸들을 비운 사본을 돌려줍니다.
     *
     * 근거 검사가 근거 없는 값을 버릴 때 씁니다.
     * 칸마다 빌더의 같은 이름 메서드로 옮기므로 인자 순서가 엇갈릴 자리가 없습니다.
     */
    public ConditionFields without(Set<String> names) {
        return builder()
                .scope(names.contains(FieldNames.SCOPE) ? null : scope)
                .guideDogOnly(names.contains(FieldNames.GUIDE_DOG_ONLY) ? null : guideDogOnly)
                .petOnly(names.contains(FieldNames.PET_ONLY) ? null : petOnly)
                .indoorAllowed(names.contains(FieldNames.INDOOR_ALLOWED) ? null : indoorAllowed)
                .outdoorAllowed(names.contains(FieldNames.OUTDOOR_ALLOWED) ? null : outdoorAllowed)
                .maxWeightKg(names.contains(FieldNames.MAX_WEIGHT_KG) ? null : maxWeightKg)
                .weightInclusive(names.contains(FieldNames.WEIGHT_INCLUSIVE) ? null : weightInclusive)
                .maxCount(names.contains(FieldNames.MAX_COUNT) ? null : maxCount)
                .sizeRule(names.contains(FieldNames.SIZE_RULE) ? null : sizeRule)
                .breedRule(names.contains(FieldNames.BREED_RULE) ? null : breedRule)
                .carrierRequired(names.contains(FieldNames.CARRIER_REQUIRED) ? null : carrierRequired)
                .leashRequired(names.contains(FieldNames.LEASH_REQUIRED) ? null : leashRequired)
                .excludedZones(names.contains(FieldNames.EXCLUDED_ZONES) ? null : excludedZones)
                .allowedZonesOnly(names.contains(FieldNames.ALLOWED_ZONES_ONLY) ? null : allowedZonesOnly)
                .excludedDays(names.contains(FieldNames.EXCLUDED_DAYS) ? null : excludedDays)
                .extraFeeAmount(names.contains(FieldNames.EXTRA_FEE_AMOUNT) ? null : extraFeeAmount)
                .extraFeeUnit(names.contains(FieldNames.EXTRA_FEE_UNIT) ? null : extraFeeUnit)
                .requiredItems(names.contains(FieldNames.REQUIRED_ITEMS) ? null : requiredItems)
                .vaccineProof(names.contains(FieldNames.VACCINE_PROOF) ? null : vaccineProof)
                .advanceInquiry(names.contains(FieldNames.ADVANCE_INQUIRY) ? null : advanceInquiry)
                .build();
    }

    private static List<String> copyOrNull(List<String> values) {
        return values == null ? null : List.copyOf(values);
    }
}
