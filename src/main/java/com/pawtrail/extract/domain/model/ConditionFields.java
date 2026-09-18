package com.pawtrail.extract.domain.model;

import com.pawtrail.extract.domain.enums.BreedRule;
import com.pawtrail.extract.domain.enums.ExtraFeeUnit;
import com.pawtrail.extract.domain.enums.Scope;
import com.pawtrail.extract.domain.enums.SizeRule;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
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
 */
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

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 20칸이 전부 비었는지 봅니다.
     *
     * 규칙 추출이 이것과 넘길 원문의 유무를 함께 보고 빈 문서를 가립니다.
     */
    public boolean isEmpty() {
        return Stream.of(scope, guideDogOnly, petOnly, indoorAllowed, outdoorAllowed,
                        maxWeightKg, weightInclusive, maxCount, sizeRule, breedRule,
                        carrierRequired, leashRequired, excludedZones, allowedZonesOnly, excludedDays,
                        extraFeeAmount, extraFeeUnit, requiredItems, vaccineProof, advanceInquiry)
                .allMatch(Objects::isNull);
    }

    private static List<String> copyOrNull(List<String> values) {
        return values == null ? null : List.copyOf(values);
    }

    /**
     * 칸을 하나씩 채워 가며 만듭니다.
     *
     * 규칙은 원문을 읽으며 채울 수 있는 칸만 채우므로 생성자 인자 스무 개를 한 번에 맞추는 것보다
     * 읽기 쉽고, 칸 순서를 잘못 넘기는 실수도 막습니다.
     */
    public static final class Builder {

        private Scope scope;
        private Boolean guideDogOnly;
        private Boolean petOnly;
        private Boolean indoorAllowed;
        private Boolean outdoorAllowed;
        private BigDecimal maxWeightKg;
        private Boolean weightInclusive;
        private Short maxCount;
        private SizeRule sizeRule;
        private BreedRule breedRule;
        private Boolean carrierRequired;
        private Boolean leashRequired;
        private List<String> excludedZones;
        private List<String> allowedZonesOnly;
        private List<String> excludedDays;
        private Integer extraFeeAmount;
        private ExtraFeeUnit extraFeeUnit;
        private List<String> requiredItems;
        private Boolean vaccineProof;
        private Boolean advanceInquiry;

        private Builder() {
        }

        public Builder scope(Scope value) {
            this.scope = value;
            return this;
        }

        public Builder guideDogOnly(Boolean value) {
            this.guideDogOnly = value;
            return this;
        }

        public Builder petOnly(Boolean value) {
            this.petOnly = value;
            return this;
        }

        public Builder indoorAllowed(Boolean value) {
            this.indoorAllowed = value;
            return this;
        }

        public Builder outdoorAllowed(Boolean value) {
            this.outdoorAllowed = value;
            return this;
        }

        public Builder maxWeightKg(BigDecimal value) {
            this.maxWeightKg = value;
            return this;
        }

        public Builder weightInclusive(Boolean value) {
            this.weightInclusive = value;
            return this;
        }

        public Builder maxCount(Short value) {
            this.maxCount = value;
            return this;
        }

        public Builder sizeRule(SizeRule value) {
            this.sizeRule = value;
            return this;
        }

        public Builder breedRule(BreedRule value) {
            this.breedRule = value;
            return this;
        }

        public Builder carrierRequired(Boolean value) {
            this.carrierRequired = value;
            return this;
        }

        public Builder leashRequired(Boolean value) {
            this.leashRequired = value;
            return this;
        }

        public Builder excludedZones(List<String> value) {
            this.excludedZones = value;
            return this;
        }

        public Builder allowedZonesOnly(List<String> value) {
            this.allowedZonesOnly = value;
            return this;
        }

        public Builder excludedDays(List<String> value) {
            this.excludedDays = value;
            return this;
        }

        public Builder extraFeeAmount(Integer value) {
            this.extraFeeAmount = value;
            return this;
        }

        public Builder extraFeeUnit(ExtraFeeUnit value) {
            this.extraFeeUnit = value;
            return this;
        }

        public Builder requiredItems(List<String> value) {
            this.requiredItems = value;
            return this;
        }

        public Builder vaccineProof(Boolean value) {
            this.vaccineProof = value;
            return this;
        }

        public Builder advanceInquiry(Boolean value) {
            this.advanceInquiry = value;
            return this;
        }

        public ConditionFields build() {
            return new ConditionFields(scope, guideDogOnly, petOnly, indoorAllowed, outdoorAllowed,
                    maxWeightKg, weightInclusive, maxCount, sizeRule, breedRule,
                    carrierRequired, leashRequired, excludedZones, allowedZonesOnly, excludedDays,
                    extraFeeAmount, extraFeeUnit, requiredItems, vaccineProof, advanceInquiry);
        }
    }
}
