package com.pawtrail.extract.domain.rule;

import com.pawtrail.extract.domain.enums.BreedRule;
import com.pawtrail.extract.domain.enums.ExtraFeeUnit;
import com.pawtrail.extract.domain.enums.ExtractionMethod;
import com.pawtrail.extract.domain.enums.Scope;
import com.pawtrail.extract.domain.enums.SizeRule;
import com.pawtrail.extract.domain.model.ConditionFields;
import com.pawtrail.extract.domain.model.Evidence;
import com.pawtrail.extract.domain.model.FieldNames;
import com.pawtrail.extract.domain.model.IntraConflict;
import com.pawtrail.extract.domain.model.LlmReading;
import com.pawtrail.extract.domain.model.MergedReading;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 한 원문에서 규칙과 모델이 읽은 조건을 칸마다 합칩니다.
 *
 * <pre>
 * 한쪽만 값이 있음        그 값과 그 근거
 * 둘 다 같은 값           규칙 값 · 근거는 둘 다
 * 둘 다 값인데 다름        그 칸을 비우고 소스 내 충돌로 남김 · 근거도 뺌
 * </pre>
 *
 * <b>가부(범위 · 실내 · 실외)는 한 묶음으로 먼저 봅니다.</b>
 * 한쪽은 동반 불가라 하고 다른 쪽은 동반된다고 하면 세 칸을 모두 비우고 범위에 한 줄만 남깁니다.
 * 칸마다 따로 보면 "고캠핑 불가능 대 소형견만 출입 허용" 이 범위 · 실외 두 줄로 쪼개지고,
 * 비운 칸 사이로 한쪽 값이 새어 나와 반만 불가인 조건이 됩니다.
 * 불가 대 허용이 아니면(둘 다 허용인데 칸이 다른 식) 세 칸도 다른 칸처럼 하나씩 봅니다.
 *
 * <b>갈린 쪽을 고르지 않습니다.</b>
 * 규칙 값을 이기게 하면 본문이 따로 말하는 사정(야외만 · 소형견만)이 사라지고,
 * 모델 값을 이기게 하면 모델이 잘못 읽은 것이 그대로 나갑니다.
 * 비워 두면 판정이 "확인 필요" 로 떨어지고 충돌 배지가 붙어, 사용자가 원문을 직접 보게 됩니다.
 *
 * 스프링을 모르는 순수 계산이라 외부 호출 없이 단위 테스트로 검증합니다.
 */
public final class ConditionMerger {

    // 가부 묶음 — 동반이 되는지 자체를 말하는 세 칸
    private static final List<String> ACCESS = List.of(
            FieldNames.SCOPE, FieldNames.INDOOR_ALLOWED, FieldNames.OUTDOOR_ALLOWED);

    private static final String JOIN = " / ";

    private ConditionMerger() {
    }

    /**
     * @param ruleFields   규칙이 채운 칸
     * @param ruleEvidence 규칙 근거
     * @param llm          근거 검사를 거친 모델 결과 — 모델에 넘길 원문이 없었으면 빈 결과
     */
    public static MergedReading merge(ConditionFields ruleFields, List<Evidence> ruleEvidence, LlmReading llm) {
        ConditionFields llmFields = llm.fields();
        Map<String, List<Evidence>> ruleByField = byField(ruleEvidence);
        Map<String, List<Evidence>> llmByField = byField(llm.evidence());

        ConditionFields.Builder merged = ConditionFields.builder();
        List<Evidence> evidence = new ArrayList<>();
        List<IntraConflict> conflicts = new ArrayList<>();
        Set<String> settled = new HashSet<>();

        // 가부가 정면으로 갈리면 세 칸을 한꺼번에 비우고 범위에 한 줄
        Access ruleAccess = access(ruleFields);
        Access llmAccess = access(llmFields);
        if (ruleAccess.opposes(llmAccess)) {
            conflicts.add(new IntraConflict(FieldNames.SCOPE,
                    texts(ruleByField, ACCESS, ruleFields),
                    texts(llmByField, ACCESS, llmFields)));
            settled.addAll(ACCESS);
        }

        for (String field : FieldNames.ALL) {
            if (settled.contains(field)) {
                continue;
            }
            Object ruleValue = ruleFields.get(field);
            Object llmValue = llmFields.get(field);
            if (ruleValue == null && llmValue == null) {
                continue;
            }
            if (ruleValue != null && llmValue != null && !sameValue(ruleValue, llmValue)) {
                conflicts.add(new IntraConflict(field,
                        texts(ruleByField, List.of(field), ruleFields),
                        texts(llmByField, List.of(field), llmFields)));
                continue;
            }
            set(merged, field, ruleValue != null ? ruleValue : llmValue);
            if (ruleValue != null) {
                evidence.addAll(ruleByField.getOrDefault(field, List.of()));
            }
            if (llmValue != null) {
                evidence.addAll(llmByField.getOrDefault(field, List.of()));
            }
        }

        return new MergedReading(merged.build(), evidence, conflicts, method(ruleFields, llmFields));
    }

    /**
     * 무엇으로 읽었는지를 합친 결과가 아니라 읽은 쪽으로 정합니다.
     *
     * 두 쪽이 갈려 모든 칸을 비웠어도 둘 다 읽은 것이므로 MIXED 입니다.
     * 조건이 하나도 없는 빈 행은 규칙이 읽고 아무것도 못 찾은 것이라 RULE 입니다.
     */
    private static ExtractionMethod method(ConditionFields ruleFields, ConditionFields llmFields) {
        boolean rule = !ruleFields.isEmpty();
        boolean model = !llmFields.isEmpty();
        if (rule && model) {
            return ExtractionMethod.MIXED;
        }
        return model ? ExtractionMethod.LLM : ExtractionMethod.RULE;
    }

    /**
     * 가부 세 칸이 동반 불가를 말하는지, 동반된다고 말하는지, 아무 말도 없는지 가립니다.
     *
     * 범위가 동반 불가이거나 실내 · 실외가 둘 다 불가면 불가입니다.
     * 범위가 전 구역 · 일부 구역이거나 실내 · 실외 중 하나라도 된다고 하면 허용입니다.
     * 실내만 불가처럼 한 칸만 막는 것은 허용 쪽의 세부라 불가로 보지 않습니다.
     */
    private static Access access(ConditionFields fields) {
        Scope scope = fields.scope();
        Boolean indoor = fields.indoorAllowed();
        Boolean outdoor = fields.outdoorAllowed();
        if (scope == Scope.NONE || (Boolean.FALSE.equals(indoor) && Boolean.FALSE.equals(outdoor))) {
            return Access.BANNED;
        }
        if (scope == Scope.ALL_AREA || scope == Scope.PARTIAL
                || Boolean.TRUE.equals(indoor) || Boolean.TRUE.equals(outdoor)) {
            return Access.ALLOWED;
        }
        return Access.SILENT;
    }

    private enum Access {
        BANNED,
        ALLOWED,
        SILENT;

        boolean opposes(Access other) {
            return (this == BANNED && other == ALLOWED) || (this == ALLOWED && other == BANNED);
        }
    }

    /**
     * 두 값이 같은 조건을 말하는지 봅니다.
     *
     * 숫자는 표기가 달라도(10 과 10.00) 같은 값이면 같습니다.
     * 목록은 순서가 달라도 같은 항목이면 같습니다. 항목의 앞뒤 공백은 보지 않습니다.
     */
    static boolean sameValue(Object left, Object right) {
        if (left instanceof BigDecimal a && right instanceof BigDecimal b) {
            return a.compareTo(b) == 0;
        }
        if (left instanceof List<?> a && right instanceof List<?> b) {
            return normalized(a).equals(normalized(b));
        }
        return Objects.equals(left, right);
    }

    private static Set<String> normalized(List<?> values) {
        return values.stream()
                .map(value -> String.valueOf(value).strip())
                .collect(Collectors.toSet());
    }

    /**
     * 갈린 자리에 남길 글을 원문 근거에서 모읍니다.
     *
     * 규칙 근거는 원문 값 그대로이고 모델 근거는 우리가 나눈 조각 그대로라, 사용자 화면에
     * 원문이 그대로 뜹니다. 근거가 없을 수는 없으나(규칙은 칸마다 근거를 남기고 모델 값은
     * 근거 검사를 거침) 만약 비면 값 자체를 적어 policy 가 빈 글로 거절하지 않게 합니다.
     */
    private static String texts(Map<String, List<Evidence>> byField, List<String> fields, ConditionFields values) {
        Set<String> texts = new LinkedHashSet<>();
        for (String field : fields) {
            for (Evidence evidence : byField.getOrDefault(field, List.of())) {
                texts.add(evidence.segmentText().strip());
            }
        }
        if (texts.isEmpty()) {
            fields.stream()
                    .map(values::get)
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .forEach(texts::add);
        }
        return String.join(JOIN, texts);
    }

    private static Map<String, List<Evidence>> byField(List<Evidence> evidence) {
        Map<String, List<Evidence>> byField = new LinkedHashMap<>();
        for (Evidence line : evidence) {
            byField.computeIfAbsent(line.fieldName(), key -> new ArrayList<>()).add(line);
        }
        return byField;
    }

    /**
     * 칸 이름으로 빌더에 값을 넣습니다.
     *
     * 이름마다 빌더의 같은 이름 메서드로 옮기므로 칸이 엇갈릴 자리가 없습니다.
     * 값의 타입은 같은 칸의 ConditionFields 에서 꺼낸 것이라 형변환이 늘 맞습니다.
     */
    @SuppressWarnings("unchecked")
    private static void set(ConditionFields.Builder builder, String field, Object value) {
        switch (field) {
            case FieldNames.SCOPE -> builder.scope((Scope) value);
            case FieldNames.GUIDE_DOG_ONLY -> builder.guideDogOnly((Boolean) value);
            case FieldNames.PET_ONLY -> builder.petOnly((Boolean) value);
            case FieldNames.INDOOR_ALLOWED -> builder.indoorAllowed((Boolean) value);
            case FieldNames.OUTDOOR_ALLOWED -> builder.outdoorAllowed((Boolean) value);
            case FieldNames.MAX_WEIGHT_KG -> builder.maxWeightKg((BigDecimal) value);
            case FieldNames.WEIGHT_INCLUSIVE -> builder.weightInclusive((Boolean) value);
            case FieldNames.MAX_COUNT -> builder.maxCount((Short) value);
            case FieldNames.SIZE_RULE -> builder.sizeRule((SizeRule) value);
            case FieldNames.BREED_RULE -> builder.breedRule((BreedRule) value);
            case FieldNames.CARRIER_REQUIRED -> builder.carrierRequired((Boolean) value);
            case FieldNames.LEASH_REQUIRED -> builder.leashRequired((Boolean) value);
            case FieldNames.EXCLUDED_ZONES -> builder.excludedZones((List<String>) value);
            case FieldNames.ALLOWED_ZONES_ONLY -> builder.allowedZonesOnly((List<String>) value);
            case FieldNames.EXCLUDED_DAYS -> builder.excludedDays((List<String>) value);
            case FieldNames.EXTRA_FEE_AMOUNT -> builder.extraFeeAmount((Integer) value);
            case FieldNames.EXTRA_FEE_UNIT -> builder.extraFeeUnit((ExtraFeeUnit) value);
            case FieldNames.REQUIRED_ITEMS -> builder.requiredItems((List<String>) value);
            case FieldNames.VACCINE_PROOF -> builder.vaccineProof((Boolean) value);
            case FieldNames.ADVANCE_INQUIRY -> builder.advanceInquiry((Boolean) value);
            default -> throw new IllegalArgumentException("조건 칸이 아닙니다: " + field);
        }
    }
}
