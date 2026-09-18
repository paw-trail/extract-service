package com.pawtrail.extract.domain.model;

import com.pawtrail.extract.domain.enums.BreedRule;
import com.pawtrail.extract.domain.enums.ExtraFeeUnit;
import com.pawtrail.extract.domain.enums.Scope;
import com.pawtrail.extract.domain.enums.SizeRule;
import lombok.Builder;

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
}
