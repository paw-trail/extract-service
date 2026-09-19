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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * 한 원문에 대한 두 읽기를 칸마다 안전 쪽으로 합칩니다.
 *
 * 두 곳에서 씁니다.
 * <pre>
 * 모델 두 번 읽기    같은 원문을 추론 medium · high 로 읽은 두 결과
 * 형제 원문          같은 장소 · 같은 소스에 붙은 서로 다른 원문 둘의 결과
 * </pre>
 *
 * <b>허용을 넓히는 값은 두 읽기가 같이 말할 때만 믿습니다.</b>
 * <pre>
 * 칸                          둘 다 값이 있는데 다름           한쪽만 값이 있음
 * 범위 · 크기 · 견종             더 좁은 쪽                     가장 넓은 값이면 비움, 아니면 그 값
 * 실내 · 실외 가능               하나라도 불가면 불가             불가면 불가, 가능이면 비움
 * 경고 칸 여섯                  하나라도 필요하면 필요            필요면 필요, 아님이면 비움
 * 체중 · 마릿수 상한             작은 쪽                        그 값
 * 체중 이하 · 미만               고른 체중을 말한 쪽 것            —
 * 제외 구역 · 제외 요일 · 준비물   합침                           그 값
 * 허용 구역만 · 추가 요금          첫 읽기 것                      그 값
 * </pre>
 * 가장 넓은 값은 전 구역 · 전 크기 · 견종 제한 없음입니다.
 * 경고 칸 여섯은 안내견만 · 반려동물 전용 · 이동장 · 목줄 · 사전 문의 · 접종 증명입니다.
 *
 * <b>갈린 자리를 소스 내 충돌로 남기지 않습니다.</b>
 * 소스 내 충돌은 정형 칸 대 본문으로 두 값을 담는 자리라, 모델의 두 읽기나 형제 원문 둘을 담으면
 * 이름표가 틀립니다. 대신 좁은 쪽 값을 고르고 그 값을 낸 읽기의 근거를 붙입니다.
 *
 * <b>근거는 고른 값을 낸 읽기 것만 둡니다.</b> 두 읽기가 같은 값이면 둘 다, 같은 근거는 한 번만 둡니다.
 * 판정 화면이 칸마다 근거를 보여 주므로 값과 근거가 늘 같은 쪽이어야 합니다.
 *
 * 스프링을 모르는 순수 계산이라 단위 테스트로 검증합니다.
 */
public final class ReadingMerger {

    private ReadingMerger() {
    }

    /**
     * 모델의 두 읽기를 합칩니다. 둘 다 근거 검사를 거친 결과여야 합니다.
     *
     * 버린 값 · 무시한 근거 · 버린 숫자는 두 읽기의 합입니다.
     */
    public static LlmReading merge(LlmReading first, LlmReading second) {
        Result result = mergeFields(first.fields(), first.evidence(), second.fields(), second.evidence());
        return new LlmReading(result.fields(), result.evidence(),
                first.droppedValues() + second.droppedValues(),
                first.ignoredCitations() + second.ignoredCitations(),
                first.droppedNumbers() + second.droppedNumbers());
    }

    /**
     * 형제 원문 둘의 결과를 합칩니다. 둘 다 규칙 · 모델 합치기와 정규화를 거친 결과입니다.
     *
     * 소스 내 충돌은 두 원문 것을 모두 둡니다. 충돌이 걸린 칸은 한쪽 원문이 스스로 갈린 자리라
     * 다른 원문에 값이 있어도 비워 둡니다. 추출 방식은 둘이 같으면 그대로, 다르면 MIXED 입니다.
     */
    public static MergedReading merge(MergedReading earlier, MergedReading later) {
        Result result = mergeFields(earlier.fields(), earlier.evidence(), later.fields(), later.evidence());

        List<IntraConflict> conflicts = new ArrayList<>(earlier.conflicts());
        later.conflicts().stream().filter(conflict -> !conflicts.contains(conflict)).forEach(conflicts::add);
        Set<String> conflicted = conflicts.stream().map(IntraConflict::fieldName).collect(Collectors.toSet());

        ConditionFields fields = result.fields().without(conflicted);
        List<Evidence> evidence = result.evidence().stream()
                .filter(line -> !conflicted.contains(line.fieldName()))
                .toList();
        ExtractionMethod method = earlier.method() == later.method() ? earlier.method() : ExtractionMethod.MIXED;
        return new MergedReading(fields, evidence, conflicts, method);
    }

    private static Result mergeFields(ConditionFields a, List<Evidence> evidenceA,
                                      ConditionFields b, List<Evidence> evidenceB) {
        Map<String, List<Evidence>> byFieldA = byField(evidenceA);
        Map<String, List<Evidence>> byFieldB = byField(evidenceB);
        ConditionFields.Builder merged = ConditionFields.builder();
        Map<String, Side> sides = new LinkedHashMap<>();

        // 범위 · 크기 · 견종 — 단계가 있는 칸
        Pick<Scope> scope = ordered(a.scope(), b.scope(), ReadingMerger::scopeRank, Scope.ALL_AREA);
        merged.scope(scope.value());
        sides.put(FieldNames.SCOPE, scope.side());
        Pick<SizeRule> size = ordered(a.sizeRule(), b.sizeRule(), ReadingMerger::sizeRank, SizeRule.ALL);
        merged.sizeRule(size.value());
        sides.put(FieldNames.SIZE_RULE, size.side());
        Pick<BreedRule> breed = ordered(a.breedRule(), b.breedRule(), ReadingMerger::breedRank, BreedRule.NONE);
        merged.breedRule(breed.value());
        sides.put(FieldNames.BREED_RULE, breed.side());

        // 실내 · 실외 — 하나라도 불가면 불가
        Pick<Boolean> indoor = gate(a.indoorAllowed(), b.indoorAllowed());
        merged.indoorAllowed(indoor.value());
        sides.put(FieldNames.INDOOR_ALLOWED, indoor.side());
        Pick<Boolean> outdoor = gate(a.outdoorAllowed(), b.outdoorAllowed());
        merged.outdoorAllowed(outdoor.value());
        sides.put(FieldNames.OUTDOOR_ALLOWED, outdoor.side());

        // 경고 칸 — 하나라도 필요하면 필요
        Pick<Boolean> guideDog = warning(a.guideDogOnly(), b.guideDogOnly());
        merged.guideDogOnly(guideDog.value());
        sides.put(FieldNames.GUIDE_DOG_ONLY, guideDog.side());
        Pick<Boolean> petOnly = warning(a.petOnly(), b.petOnly());
        merged.petOnly(petOnly.value());
        sides.put(FieldNames.PET_ONLY, petOnly.side());
        Pick<Boolean> carrier = warning(a.carrierRequired(), b.carrierRequired());
        merged.carrierRequired(carrier.value());
        sides.put(FieldNames.CARRIER_REQUIRED, carrier.side());
        Pick<Boolean> leash = warning(a.leashRequired(), b.leashRequired());
        merged.leashRequired(leash.value());
        sides.put(FieldNames.LEASH_REQUIRED, leash.side());
        Pick<Boolean> inquiry = warning(a.advanceInquiry(), b.advanceInquiry());
        merged.advanceInquiry(inquiry.value());
        sides.put(FieldNames.ADVANCE_INQUIRY, inquiry.side());
        Pick<Boolean> vaccine = warning(a.vaccineProof(), b.vaccineProof());
        merged.vaccineProof(vaccine.value());
        sides.put(FieldNames.VACCINE_PROOF, vaccine.side());

        // 체중 · 마릿수 상한 — 작은 쪽 · 이하 · 미만은 고른 체중을 따라감
        Pick<BigDecimal> weight = smaller(a.maxWeightKg(), b.maxWeightKg());
        merged.maxWeightKg(weight.value());
        sides.put(FieldNames.MAX_WEIGHT_KG, weight.side());
        Pick<Boolean> inclusive = inclusive(weight.side(), a.weightInclusive(), b.weightInclusive());
        merged.weightInclusive(inclusive.value());
        sides.put(FieldNames.WEIGHT_INCLUSIVE, inclusive.side());
        Pick<Short> count = smaller(a.maxCount(), b.maxCount());
        merged.maxCount(count.value());
        sides.put(FieldNames.MAX_COUNT, count.side());

        // 제외 구역 · 제외 요일 · 준비물 — 합침
        Pick<List<String>> excludedZones = union(a.excludedZones(), b.excludedZones());
        merged.excludedZones(excludedZones.value());
        sides.put(FieldNames.EXCLUDED_ZONES, excludedZones.side());
        Pick<List<String>> excludedDays = union(a.excludedDays(), b.excludedDays());
        merged.excludedDays(excludedDays.value());
        sides.put(FieldNames.EXCLUDED_DAYS, excludedDays.side());
        Pick<List<String>> items = union(a.requiredItems(), b.requiredItems());
        merged.requiredItems(items.value());
        sides.put(FieldNames.REQUIRED_ITEMS, items.side());

        // 허용 구역만 · 추가 요금 — 첫 읽기 것 · 요금 단위는 금액을 낸 쪽을 따라감
        Pick<List<String>> allowed = firstPresent(a.allowedZonesOnly(), b.allowedZonesOnly());
        merged.allowedZonesOnly(allowed.value());
        sides.put(FieldNames.ALLOWED_ZONES_ONLY, allowed.side());
        Pick<Integer> fee = firstPresent(a.extraFeeAmount(), b.extraFeeAmount());
        merged.extraFeeAmount(fee.value());
        sides.put(FieldNames.EXTRA_FEE_AMOUNT, fee.side());
        Pick<ExtraFeeUnit> unit = switch (fee.side()) {
            case FIRST -> new Pick<>(a.extraFeeUnit(), a.extraFeeUnit() == null ? Side.NONE : Side.FIRST);
            case SECOND -> new Pick<>(b.extraFeeUnit(), b.extraFeeUnit() == null ? Side.NONE : Side.SECOND);
            default -> firstPresent(a.extraFeeUnit(), b.extraFeeUnit());
        };
        merged.extraFeeUnit(unit.value());
        sides.put(FieldNames.EXTRA_FEE_UNIT, unit.side());

        List<Evidence> evidence = new ArrayList<>();
        for (String field : FieldNames.ALL) {
            Side side = sides.getOrDefault(field, Side.NONE);
            if (side == Side.FIRST || side == Side.BOTH) {
                addDistinct(evidence, byFieldA.getOrDefault(field, List.of()));
            }
            if (side == Side.SECOND || side == Side.BOTH) {
                addDistinct(evidence, byFieldB.getOrDefault(field, List.of()));
            }
        }
        return new Result(merged.build(), evidence);
    }

    /**
     * 단계가 있는 칸 — 둘 다 값이면 좁은 쪽, 한쪽만이면 가장 넓은 값은 비움.
     *
     * 순위는 작을수록 좁습니다(동반 불가 · 소형견만 · 맹견 금지 쪽).
     */
    private static <T> Pick<T> ordered(T a, T b, ToIntFunction<T> rank, T widest) {
        if (a != null && b != null) {
            if (a.equals(b)) {
                return new Pick<>(a, Side.BOTH);
            }
            return rank.applyAsInt(a) < rank.applyAsInt(b) ? new Pick<>(a, Side.FIRST) : new Pick<>(b, Side.SECOND);
        }
        if (a != null) {
            return a.equals(widest) ? Pick.none() : new Pick<>(a, Side.FIRST);
        }
        if (b != null) {
            return b.equals(widest) ? Pick.none() : new Pick<>(b, Side.SECOND);
        }
        return Pick.none();
    }

    // 실내 · 실외 — 불가는 한쪽만 말해도 믿고, 가능은 둘이 같이 말할 때만
    private static Pick<Boolean> gate(Boolean a, Boolean b) {
        boolean closedA = Boolean.FALSE.equals(a);
        boolean closedB = Boolean.FALSE.equals(b);
        if (closedA || closedB) {
            return new Pick<>(false, closedA && closedB ? Side.BOTH : closedA ? Side.FIRST : Side.SECOND);
        }
        if (Boolean.TRUE.equals(a) && Boolean.TRUE.equals(b)) {
            return new Pick<>(true, Side.BOTH);
        }
        return Pick.none();
    }

    // 경고 칸 — 필요는 한쪽만 말해도 믿고, 아님은 둘이 같이 말할 때만
    private static Pick<Boolean> warning(Boolean a, Boolean b) {
        boolean requiredA = Boolean.TRUE.equals(a);
        boolean requiredB = Boolean.TRUE.equals(b);
        if (requiredA || requiredB) {
            return new Pick<>(true, requiredA && requiredB ? Side.BOTH : requiredA ? Side.FIRST : Side.SECOND);
        }
        if (Boolean.FALSE.equals(a) && Boolean.FALSE.equals(b)) {
            return new Pick<>(false, Side.BOTH);
        }
        return Pick.none();
    }

    // 상한 — 작은 쪽 · 한쪽만이면 그 값
    private static <T extends Comparable<T>> Pick<T> smaller(T a, T b) {
        if (a != null && b != null) {
            int compared = a.compareTo(b);
            if (compared == 0) {
                return new Pick<>(a, Side.BOTH);
            }
            return compared < 0 ? new Pick<>(a, Side.FIRST) : new Pick<>(b, Side.SECOND);
        }
        if (a != null) {
            return new Pick<>(a, Side.FIRST);
        }
        return b != null ? new Pick<>(b, Side.SECOND) : Pick.none();
    }

    /**
     * 이하 · 미만 — 고른 체중을 말한 쪽 것.
     *
     * 두 체중이 같은데 한쪽은 이하 · 한쪽은 미만이면 미만(좁은 쪽)입니다.
     * 체중이 없으면 이하 · 미만만 남아도 뜻이 없어 비웁니다.
     */
    private static Pick<Boolean> inclusive(Side weightSide, Boolean a, Boolean b) {
        return switch (weightSide) {
            case FIRST -> a == null ? Pick.none() : new Pick<>(a, Side.FIRST);
            case SECOND -> b == null ? Pick.none() : new Pick<>(b, Side.SECOND);
            case BOTH -> {
                if (a != null && a.equals(b)) {
                    yield new Pick<>(a, Side.BOTH);
                }
                if (Boolean.FALSE.equals(a)) {
                    yield new Pick<>(false, Side.FIRST);
                }
                if (Boolean.FALSE.equals(b)) {
                    yield new Pick<>(false, Side.SECOND);
                }
                yield a != null ? new Pick<>(a, Side.FIRST) : b != null ? new Pick<>(b, Side.SECOND) : Pick.none();
            }
            case NONE -> Pick.none();
        };
    }

    // 제한 목록 — 합침 · 앞뒤 공백만 다른 항목은 하나로
    private static Pick<List<String>> union(List<String> a, List<String> b) {
        boolean hasA = a != null && !a.isEmpty();
        boolean hasB = b != null && !b.isEmpty();
        if (!hasA && !hasB) {
            return Pick.none();
        }
        List<String> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (List<String> list : List.of(hasA ? a : List.<String>of(), hasB ? b : List.<String>of())) {
            for (String item : list) {
                if (seen.add(item.strip())) {
                    items.add(item);
                }
            }
        }
        return new Pick<>(items, hasA && hasB ? Side.BOTH : hasA ? Side.FIRST : Side.SECOND);
    }

    private static <T> Pick<T> firstPresent(T a, T b) {
        if (a instanceof List<?> list && list.isEmpty()) {
            a = null;
        }
        if (a != null) {
            return new Pick<>(a, Side.FIRST);
        }
        if (b instanceof List<?> list && list.isEmpty()) {
            b = null;
        }
        return b != null ? new Pick<>(b, Side.SECOND) : Pick.none();
    }

    private static int scopeRank(Scope scope) {
        return switch (scope) {
            case NONE -> 0;
            case PARTIAL -> 1;
            case ALL_AREA -> 2;
        };
    }

    private static int sizeRank(SizeRule size) {
        return switch (size) {
            case SMALL_ONLY -> 0;
            case SMALL_MEDIUM -> 1;
            case ALL -> 2;
        };
    }

    private static int breedRank(BreedRule breed) {
        return switch (breed) {
            case DANGEROUS_BANNED -> 0;
            case DANGEROUS_MUZZLE -> 1;
            case NONE -> 2;
        };
    }

    private static Map<String, List<Evidence>> byField(List<Evidence> evidence) {
        Map<String, List<Evidence>> byField = new LinkedHashMap<>();
        for (Evidence line : evidence) {
            byField.computeIfAbsent(line.fieldName(), key -> new ArrayList<>()).add(line);
        }
        return byField;
    }

    private static void addDistinct(List<Evidence> target, List<Evidence> lines) {
        for (Evidence line : lines) {
            if (!target.contains(line)) {
                target.add(line);
            }
        }
    }

    // 고른 값이 어느 읽기에서 왔는지 — 근거를 어느 쪽에서 가져올지 정함
    private enum Side {
        FIRST,
        SECOND,
        BOTH,
        NONE
    }

    private record Pick<T>(T value, Side side) {

        static <T> Pick<T> none() {
            return new Pick<>(null, Side.NONE);
        }
    }

    private record Result(ConditionFields fields, List<Evidence> evidence) {
    }
}
