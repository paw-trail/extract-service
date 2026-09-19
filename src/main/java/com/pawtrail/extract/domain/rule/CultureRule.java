package com.pawtrail.extract.domain.rule;

import com.pawtrail.extract.domain.enums.Scope;
import com.pawtrail.extract.domain.enums.SizeRule;
import com.pawtrail.extract.domain.model.ConditionFields;
import com.pawtrail.extract.domain.model.CultureRaw;
import com.pawtrail.extract.domain.model.Evidence;
import com.pawtrail.extract.domain.model.FieldNames;
import com.pawtrail.extract.domain.model.RuleResult;
import com.pawtrail.extract.domain.model.SourceText;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.pawtrail.extract.domain.rule.RuleTexts.addText;
import static com.pawtrail.extract.domain.rule.RuleTexts.trimToNull;

/**
 * 한국문화정보원 반려동물 동반 가능 문화시설 원문의 규칙입니다.
 *
 * <b>동물병원은 보내지 않습니다.</b>
 * 반려 칸이 전부 "가능" 쪽이라 그대로 뽑으면 병원에 동반 조건이 생기고,
 * 진료 대상 크기인 "대형" 이 입장 크기 조건으로 읽힙니다. 동물병원은 판정 대상이 아닙니다.
 *
 * <b>동반 N 은 동반 불가입니다.</b>
 * scope NONE 과 실내 · 실외 false 를 함께 적고 나머지 칸은 읽지 않습니다.
 * 그 행의 "해당없음" · "없음" 은 불가라서 채운 자리이지 조건이 아닙니다.
 *
 * <b>"모두 · 없음" 류는 그 칸만 다루는 항목이 한 말일 때만 값입니다.</b>
 * <pre>
 * 크기 "모두 가능"             sizeRule ALL
 * 요금 "없음"                  extraFeeAmount 0
 * 전용 "해당없음"               petOnly false — 반려견 없이도 입장
 * 제한사항 "제한사항 없음"       비움 — 목줄 · 이동장 · 견종 · 마릿수를 한 번에 뭉뚱그린 말
 * </pre>
 *
 * <b>크기와 요금은 단순 패턴만 규칙이 읽습니다.</b>
 * <pre>
 * 모두 가능 · 소형 · 소형/중형     ALL · SMALL_ONLY · SMALL_MEDIUM
 * N kg 미만                      maxWeightKg N · weightInclusive false
 * N kg 이하 · 이내                maxWeightKg N · weightInclusive true
 * 뒤에 "소형"                     위에 SMALL_ONLY 를 더함
 * N원                            extraFeeAmount N · 단위는 비움 (마리당인지 1박당인지 말하지 않음)
 * </pre>
 * 이 패턴이 크기 8,920건 중 8,900건, 요금 8,885건을 덮습니다.
 * "주말 및 공휴일은 13kg 이하" 처럼 패턴 밖에 있는 것은 LLM 에 넘깁니다.
 *
 * <b>근거는 원문 값 그대로 쓰고, Y/N 만 칸 이름을 앞에 붙입니다.</b>
 * "Y" 한 글자로는 무엇이 된다는 것인지 드러나지 않습니다.
 */
public final class CultureRule {

    static final String VET = "동물병원";
    static final String YES = "Y";
    static final String NO = "N";
    static final String ALL_SIZES = "모두 가능";
    static final String SMALL = "소형";
    static final String SMALL_MEDIUM = "소형/중형";
    static final String NO_FEE = "없음";
    static final String NOT_APPLICABLE = "해당없음";
    static final String NO_RESTRICTION = "제한사항 없음";
    static final String PET_ONLY = "반려동물 전용";

    // "5kg 미만 소형" · "10kg 이하" · "10 KG 이내" 를 받음
    // 앞뒤로 다른 말이 붙으면(주말 · 범위 · 체고) 패턴 밖으로 보고 LLM 에 넘김
    static final Pattern WEIGHT = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*[kK][gG]\\s*(미만|이하|이내)(\\s*소형)?");

    // "20,000원" 을 받음 — 범위 · 변동 · 크기별 금액은 LLM 에 넘김
    static final Pattern FEE = Pattern.compile("[\\d,]+원");

    private CultureRule() {
    }

    public static RuleResult extract(CultureRaw raw) {
        if (VET.equals(trimToNull(raw.category3()))) {
            return RuleResult.skipDone("문화정보원 동물병원 — 판정 대상이 아님");
        }

        ConditionFields.Builder fields = ConditionFields.builder();
        List<Evidence> evidence = new ArrayList<>();
        List<SourceText> texts = new ArrayList<>();

        String companion = trimToNull(raw.companion());
        if (NO.equals(companion)) {
            String text = labeled(CultureRaw.KEY_COMPANION, companion);
            fields.scope(Scope.NONE).indoorAllowed(false).outdoorAllowed(false);
            evidence.add(Evidence.ofRule(FieldNames.SCOPE, CultureRaw.KEY_COMPANION, text));
            evidence.add(Evidence.ofRule(FieldNames.INDOOR_ALLOWED, CultureRaw.KEY_COMPANION, text));
            evidence.add(Evidence.ofRule(FieldNames.OUTDOOR_ALLOWED, CultureRaw.KEY_COMPANION, text));
            return RuleResult.of(fields.build(), evidence, texts);
        }
        if (!YES.equals(companion)) {
            // 실물은 Y · N 둘뿐임 — 다른 값이면 가부를 모르므로 아무 칸도 읽지 않음
            return RuleResult.of(fields.build(), evidence, texts);
        }

        readInOut(fields, evidence, raw.indoor(), CultureRaw.KEY_INDOOR, true);
        readInOut(fields, evidence, raw.outdoor(), CultureRaw.KEY_OUTDOOR, false);
        readPetOnly(fields, evidence, raw.petOnly());
        readSize(fields, evidence, texts, raw.size());
        readFee(fields, evidence, texts, raw.fee());

        String restriction = trimToNull(raw.restriction());
        if (restriction != null && !NO_RESTRICTION.equals(restriction) && !NOT_APPLICABLE.equals(restriction)) {
            addText(texts, CultureRaw.KEY_RESTRICTION, restriction);
        }

        return RuleResult.of(fields.build(), evidence, texts);
    }

    private static void readInOut(ConditionFields.Builder fields, List<Evidence> evidence,
                                  String value, String key, boolean indoor) {
        String yn = trimToNull(value);
        Boolean allowed = YES.equals(yn) ? Boolean.TRUE : NO.equals(yn) ? Boolean.FALSE : null;
        if (allowed == null) {
            return;
        }
        String fieldName = indoor ? FieldNames.INDOOR_ALLOWED : FieldNames.OUTDOOR_ALLOWED;
        if (indoor) {
            fields.indoorAllowed(allowed);
        } else {
            fields.outdoorAllowed(allowed);
        }
        evidence.add(Evidence.ofRule(fieldName, key, labeled(key, yn)));
    }

    private static void readPetOnly(ConditionFields.Builder fields, List<Evidence> evidence, String value) {
        String petOnly = trimToNull(value);
        if (PET_ONLY.equals(petOnly)) {
            fields.petOnly(true);
        } else if (NOT_APPLICABLE.equals(petOnly)) {
            fields.petOnly(false);
        } else {
            return;
        }
        evidence.add(Evidence.ofRule(FieldNames.PET_ONLY, CultureRaw.KEY_PET_ONLY, petOnly));
    }

    private static void readSize(ConditionFields.Builder fields, List<Evidence> evidence,
                                 List<SourceText> texts, String value) {
        String size = trimToNull(value);
        if (size == null || NOT_APPLICABLE.equals(size) || NO_FEE.equals(size)) {
            return;
        }
        SizeRule sizeRule = switch (size) {
            case ALL_SIZES -> SizeRule.ALL;
            case SMALL -> SizeRule.SMALL_ONLY;
            case SMALL_MEDIUM -> SizeRule.SMALL_MEDIUM;
            default -> null;
        };
        if (sizeRule != null) {
            fields.sizeRule(sizeRule);
            evidence.add(Evidence.ofRule(FieldNames.SIZE_RULE, CultureRaw.KEY_SIZE, size));
            return;
        }

        Matcher weight = WEIGHT.matcher(size);
        if (!weight.matches()) {
            texts.add(new SourceText(CultureRaw.KEY_SIZE, size));
            return;
        }
        fields.maxWeightKg(new BigDecimal(weight.group(1)))
                .weightInclusive(!"미만".equals(weight.group(2)));
        evidence.add(Evidence.ofRule(FieldNames.MAX_WEIGHT_KG, CultureRaw.KEY_SIZE, size));
        evidence.add(Evidence.ofRule(FieldNames.WEIGHT_INCLUSIVE, CultureRaw.KEY_SIZE, size));
        if (weight.group(3) != null) {
            fields.sizeRule(SizeRule.SMALL_ONLY);
            evidence.add(Evidence.ofRule(FieldNames.SIZE_RULE, CultureRaw.KEY_SIZE, size));
        }
    }

    private static void readFee(ConditionFields.Builder fields, List<Evidence> evidence,
                                List<SourceText> texts, String value) {
        String fee = trimToNull(value);
        if (fee == null || NOT_APPLICABLE.equals(fee)) {
            return;
        }
        Integer amount = NO_FEE.equals(fee) ? Integer.valueOf(0)
                : FEE.matcher(fee).matches() ? Integer.valueOf(fee.replace(",", "").replace("원", ""))
                : null;
        if (amount == null) {
            texts.add(new SourceText(CultureRaw.KEY_FEE, fee));
            return;
        }
        fields.extraFeeAmount(amount);
        evidence.add(Evidence.ofRule(FieldNames.EXTRA_FEE_AMOUNT, CultureRaw.KEY_FEE, fee));
    }

    // Y/N 은 값만으로 뜻이 드러나지 않아 칸 이름을 앞에 붙임
    private static String labeled(String key, String yn) {
        return key + " " + yn;
    }
}
