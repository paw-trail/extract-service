package com.pawtrail.extract.domain.rule;

import com.pawtrail.extract.domain.model.ConditionFields;
import com.pawtrail.extract.domain.model.Evidence;
import com.pawtrail.extract.domain.model.FieldNames;
import com.pawtrail.extract.domain.model.IntraConflict;
import com.pawtrail.extract.domain.model.PolicyItem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * policy bulk 가 400 으로 막는 규칙을 보내기 전에 문서 하나씩 검사합니다.
 *
 * <b>policy 는 한 항목만 걸려도 청크 전체를 400 으로 돌려보냅니다.</b>
 * 그러면 멀쩡한 99건까지 보내지 못하고 실행이 멈춥니다.
 * 같은 규칙을 여기서 먼저 보고 걸리는 문서만 실패로 두면 나머지는 그대로 나갑니다.
 * 모델이 3자리를 넘는 체중이나 0마리 같은 값을 낼 수 있어 실제로 걸릴 자리입니다.
 *
 * <b>규칙은 policy 의 요청 검증과 같습니다.</b> (BulkUpsertRequest · BulkItemRequest ·
 * PolicyFieldsRequest · EvidenceRequest · ConflictRequest) 저쪽 규칙이 바뀌면 여기도 함께 고칩니다.
 * 여기서 놓친 것은 저쪽이 400 으로 막고, 실행이 멈춰 드러납니다.
 */
public final class PolicyItemCheck {

    // 이름 칸의 길이 상한 — 근거 · 충돌의 조건 이름과 원문 키
    private static final int NAME_MAX = 40;
    private static final int EXTRACTED_BY_MAX = 50;
    private static final int PROMPT_VERSION_MAX = 20;

    // 체중 상한 — numeric(5,2) 라 정수 3자리 · 소수 2자리
    private static final int WEIGHT_INTEGER_DIGITS = 3;
    private static final int WEIGHT_FRACTION_DIGITS = 2;

    private PolicyItemCheck() {
    }

    /**
     * 항목 하나의 어긋난 자리를 모읍니다. 비어 있으면 보내도 됩니다.
     */
    public static List<String> check(PolicyItem item) {
        List<String> problems = new ArrayList<>();
        if (item.placeId() == null) {
            problems.add("장소 식별자가 없음");
        }
        if (item.source() == null) {
            problems.add("소스가 없음");
        }
        if (item.fields() == null) {
            problems.add("조건이 없음");
        } else {
            checkFields(item.fields(), problems);
        }
        if (item.method() == null) {
            problems.add("추출 방식이 없음");
        }
        for (Evidence evidence : item.evidence()) {
            checkEvidence(evidence, problems);
        }
        for (IntraConflict conflict : item.conflicts()) {
            checkConflict(conflict, problems);
        }
        return problems;
    }

    /**
     * 실행 전체에 한 번 — 모델 이름과 프롬프트 판의 길이입니다.
     *
     * 이것이 걸리면 모든 청크가 400 이라 문서를 실패로 두지 않고 실행을 시작하지 않습니다.
     */
    public static List<String> checkBatch(String extractedBy, String promptVersion) {
        List<String> problems = new ArrayList<>();
        if (extractedBy != null && extractedBy.length() > EXTRACTED_BY_MAX) {
            problems.add("모델 이름이 " + EXTRACTED_BY_MAX + "자를 넘음: " + extractedBy);
        }
        if (promptVersion != null && promptVersion.length() > PROMPT_VERSION_MAX) {
            problems.add("프롬프트 판이 " + PROMPT_VERSION_MAX + "자를 넘음: " + promptVersion);
        }
        return problems;
    }

    private static void checkFields(ConditionFields fields, List<String> problems) {
        BigDecimal weight = fields.maxWeightKg();
        if (weight != null) {
            if (weight.signum() <= 0) {
                problems.add("체중 상한이 0 이하: " + weight.toPlainString());
            }
            int fraction = Math.max(weight.scale(), 0);
            int integer = weight.precision() - weight.scale();
            if (integer > WEIGHT_INTEGER_DIGITS || fraction > WEIGHT_FRACTION_DIGITS) {
                problems.add("체중 상한의 자릿수가 맞지 않음: " + weight.toPlainString());
            }
        }
        if (fields.maxCount() != null && fields.maxCount() < 1) {
            problems.add("마릿수 상한이 1 미만: " + fields.maxCount());
        }
        if (fields.extraFeeAmount() != null && fields.extraFeeAmount() < 0) {
            problems.add("추가 요금이 음수: " + fields.extraFeeAmount());
        }
    }

    private static void checkEvidence(Evidence evidence, List<String> problems) {
        if (!FieldNames.ALL.contains(evidence.fieldName())) {
            problems.add("근거의 조건 이름이 20칸에 없음: " + evidence.fieldName());
        }
        if (isBlank(evidence.originField()) || evidence.originField().length() > NAME_MAX) {
            problems.add("근거의 원문 키가 비었거나 " + NAME_MAX + "자를 넘음: " + evidence.originField());
        }
        if (isBlank(evidence.segmentText())) {
            problems.add("근거 문구가 비어 있음: " + evidence.fieldName());
        }
    }

    private static void checkConflict(IntraConflict conflict, List<String> problems) {
        if (!FieldNames.ALL.contains(conflict.fieldName())) {
            problems.add("충돌의 조건 이름이 20칸에 없음: " + conflict.fieldName());
        }
        if (isBlank(conflict.fieldText()) || isBlank(conflict.bodyText())) {
            problems.add("충돌의 두 값 중 비어 있는 것이 있음: " + conflict.fieldName());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
