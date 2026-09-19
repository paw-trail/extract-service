package com.pawtrail.extract.domain.rule;

import com.pawtrail.extract.domain.enums.ExtractionMethod;
import com.pawtrail.extract.domain.enums.Scope;
import com.pawtrail.extract.domain.model.ConditionFields;
import com.pawtrail.extract.domain.model.Evidence;
import com.pawtrail.extract.domain.model.FieldNames;
import com.pawtrail.extract.domain.model.IntraConflict;
import com.pawtrail.extract.domain.model.MergedReading;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 합친 결과에서 서로 따라오는 칸을 채웁니다.
 *
 * <pre>
 * ① 범위가 동반 불가(NONE)     비어 있는 실내 · 실외를 false 로
 * ② 범위가 비어 있는데         허용 구역이나 제외 구역이 있거나
 *                             실내 false · 실외 true 이면 범위를 일부 구역(PARTIAL)으로
 *                             — 그 칸을 모델이 읽었을 때만
 * </pre>
 *
 * <b>정확도 평가에서 정답 기준과 모델 출력이 갈린 자리입니다.</b>
 * 모델은 "동반 불가" 를 범위로만 말하고 실내 · 실외를 비워 두는 일이 잦았고,
 * "반려 구역만 가능" 처럼 구역을 말하면서 범위를 비워 두는 일도 잦았습니다.
 * 뜻이 칸 사이에서 따라 나오는 것이라 프롬프트로 가르치기보다 코드가 채우는 편이 늘 같습니다.
 * ① 은 판정이 실내 · 실외를 볼 때 "정보 없음" 이 아니라 "불가" 를 보게 하고,
 * ② 는 구역 조건이 있는 장소가 범위 칸에서 전 구역과 갈려 충돌로 잡히게 합니다.
 *
 * <b>② 는 모델이 읽은 칸에만 겁니다.</b> 근거 줄의 추출 방식으로 가립니다.
 * ② 는 모델이 "야외만" 을 읽고 범위를 비우는 일을 메우려는 규칙인데, 규칙 결과에도 걸리면
 * 한국문화정보원의 실외만 되는 곳은 일부 구역이 되고 실내만 되는 곳은 범위가 비어 두 쪽이 어긋납니다.
 * 규칙이 읽은 칸은 원문 칸이 말한 그대로 두어 두 쪽을 맞춥니다.
 * 실내 · 실외는 두 칸을 모두 모델이 읽었을 때만 봅니다.
 *
 * <b>채우기만 하고 덮지 않습니다.</b> 이미 값이 있는 칸은 그대로 둡니다.
 * 소스 내 충돌로 비운 칸도 채우지 않습니다 — 비운 이유가 "두 쪽이 갈렸다" 인데
 * 한쪽에서 따라 나온 값을 다시 넣으면 그 판단을 뒤집게 됩니다.
 *
 * <b>채운 칸의 근거는 원래 칸의 근거를 옮겨 씁니다.</b>
 * 판정 화면이 칸마다 근거 문장을 보여 주므로, 채운 칸에 근거가 없으면 그 자리가 비어 보입니다.
 * 읽은 쪽(추출 방식)도 원래 근거의 것을 그대로 옮깁니다.
 */
public final class ConditionNormalizer {

    private ConditionNormalizer() {
    }

    public static MergedReading normalize(MergedReading reading) {
        ConditionFields fields = reading.fields();
        Set<String> conflicted = reading.conflicts().stream()
                .map(IntraConflict::fieldName)
                .collect(Collectors.toSet());
        List<Evidence> evidence = new ArrayList<>(reading.evidence());
        ConditionFields.Builder builder = fields.toBuilder();

        // ① 동반 불가면 실내 · 실외도 불가
        if (fields.scope() == Scope.NONE) {
            if (fields.indoorAllowed() == null && !conflicted.contains(FieldNames.INDOOR_ALLOWED)) {
                builder.indoorAllowed(false);
                evidence.addAll(moved(reading.evidence(), List.of(FieldNames.SCOPE), FieldNames.INDOOR_ALLOWED));
            }
            if (fields.outdoorAllowed() == null && !conflicted.contains(FieldNames.OUTDOOR_ALLOWED)) {
                builder.outdoorAllowed(false);
                evidence.addAll(moved(reading.evidence(), List.of(FieldNames.SCOPE), FieldNames.OUTDOOR_ALLOWED));
            }
        }

        // ② 구역을 말하거나 실내만 막으면 일부 구역 — 모델이 읽은 칸일 때만
        if (fields.scope() == null && !conflicted.contains(FieldNames.SCOPE)) {
            List<String> sources = partialSources(fields, readByModel(reading.evidence()));
            if (!sources.isEmpty()) {
                builder.scope(Scope.PARTIAL);
                evidence.addAll(moved(reading.evidence(), sources, FieldNames.SCOPE));
            }
        }

        return new MergedReading(builder.build(), evidence, reading.conflicts(), reading.method());
    }

    /**
     * 범위를 일부 구역으로 볼 근거가 되는 칸들입니다. 비어 있으면 채우지 않습니다.
     *
     * @param byModel 모델 근거가 하나라도 있는 칸 — 여기에 없는 칸은 보지 않음
     */
    private static List<String> partialSources(ConditionFields fields, Set<String> byModel) {
        List<String> sources = new ArrayList<>();
        if (hasItems(fields.allowedZonesOnly()) && byModel.contains(FieldNames.ALLOWED_ZONES_ONLY)) {
            sources.add(FieldNames.ALLOWED_ZONES_ONLY);
        }
        if (hasItems(fields.excludedZones()) && byModel.contains(FieldNames.EXCLUDED_ZONES)) {
            sources.add(FieldNames.EXCLUDED_ZONES);
        }
        if (Boolean.FALSE.equals(fields.indoorAllowed()) && Boolean.TRUE.equals(fields.outdoorAllowed())
                && byModel.contains(FieldNames.INDOOR_ALLOWED) && byModel.contains(FieldNames.OUTDOOR_ALLOWED)) {
            sources.add(FieldNames.INDOOR_ALLOWED);
            sources.add(FieldNames.OUTDOOR_ALLOWED);
        }
        return sources;
    }

    /**
     * 모델이 읽은 칸들입니다. 그 칸의 근거 가운데 LLM 이 하나라도 있으면 모델이 읽은 것으로 봅니다.
     *
     * 규칙과 모델이 같은 값을 읽으면 근거가 둘 다 남는데, 그때도 모델이 읽은 칸입니다.
     */
    private static Set<String> readByModel(List<Evidence> evidence) {
        return evidence.stream()
                .filter(line -> line.extractionMethod() == ExtractionMethod.LLM)
                .map(Evidence::fieldName)
                .collect(Collectors.toSet());
    }

    private static boolean hasItems(List<String> values) {
        return values != null && !values.isEmpty();
    }

    /**
     * 원래 칸들의 근거를 채운 칸의 이름으로 옮겨 적습니다. 같은 문구는 한 번만 둡니다.
     * 읽은 쪽이 다른 두 줄은 문구가 같아도 따로 둡니다.
     */
    private static List<Evidence> moved(List<Evidence> evidence, List<String> from, String to) {
        List<Evidence> moved = new ArrayList<>();
        for (Evidence line : evidence) {
            if (!from.contains(line.fieldName())) {
                continue;
            }
            Evidence copy = new Evidence(to, line.originField(), line.segmentIndex(), line.segmentText(),
                    line.extractionMethod());
            if (!moved.contains(copy)) {
                moved.add(copy);
            }
        }
        return moved;
    }
}
