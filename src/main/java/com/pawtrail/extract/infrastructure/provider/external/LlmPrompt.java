package com.pawtrail.extract.infrastructure.provider.external;

import com.pawtrail.extract.domain.model.FieldNames;
import com.pawtrail.extract.domain.model.Segment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 두 구현(Ollama · OpenAI)이 함께 쓰는 프롬프트 · 응답 스키마 · 입력 글입니다.
 *
 * 전송만 구현마다 갈리고 모델에게 무엇을 어떻게 묻는지는 여기 한 곳에 있습니다.
 * 한쪽만 고치면 로컬과 배포가 다른 질문을 하게 되어 결과를 견줄 수 없습니다.
 *
 * <b>프롬프트는 실물 표본으로 다듬어 확정한 판입니다.</b>
 * 2026.9.13 원문 덤프의 표본 28건(공사 · 고캠핑 · 문화정보원 · 회귀 · 입마개 세 갈래)으로
 * 네 번 고쳐 돌린 뒤 정했습니다. 판을 올릴 때는 VERSION 을 함께 올립니다.
 * 추출 기록(promptVersion)에 남아 어느 판으로 뽑은 조건인지 가려집니다.
 *
 * <b>스키마는 두 구현이 같은 것을 씁니다.</b>
 * 타입 · enum · required · additionalProperties false · null 허용만 씁니다.
 * 개수 제약(minItems · maxItems)은 OpenAI strict 가 받는지 자료가 엇갈려 두지 않았고,
 * 근거를 빠뜨리는 것은 CitationCheck 가 잡습니다.
 * 조건 칸을 앞에, 근거를 뒤에 둡니다 — 시험에서 순서가 섞이자 모델이 근거를 먼저 썼습니다.
 */
public final class LlmPrompt {

    public static final String VERSION = "v1";

    public static final String SYSTEM = """
            반려동물 동반 조건을 원문 조각에서 뽑아 JSON 으로 답한다.

            규칙
            1. 원문이 말한 것만 적는다. 원문이 말하지 않은 칸은 null 이다.
            2. false 는 원문이 "안 된다" 또는 "필요 없다" 라고 말했을 때만 쓴다. 어떤 물건이 목록에 없다는 것만으로 false 로 적지 않는다.
            3. 여러 조건을 뭉뚱그린 말("제한사항 없음", "자유이용")로는 칸을 채우지 않는다.
            4. 같은 원문에 더 구체적인 말이 있으면 그 말을 따른다. "전 견종 동반 가능" 과 "맹견은 입마개 필수" 가 함께 있으면 sizeRule 은 ALL, breedRule 은 DANGEROUS_MUZZLE 이다. 필요사항에 "입마개 착용" 이 있어도 다른 조각이 "맹견의 경우", "15kg 이상" 처럼 입마개가 필요한 개를 좁히면 그 좁힌 말을 따른다.
            5. 목줄, 이동장, 유모차, 예방접종 증명처럼 따로 칸이 있는 것은 requiredItems 에 넣지 않는다.
            6. 요일, 계절, 시간, 크기에 따라 조건이 갈리거나 값이 범위로 나오면 그 칸에 한 값을 고르지 않고 null 로 둔다. "주말 및 공휴일은 13kg 이하" 는 maxWeightKg 가 null 이고, "5,000~6,000원" 은 extraFeeAmount 가 null 이다.
            7. excludedDays, excludedZones 와 indoorAllowed, outdoorAllowed 의 false 는 그 날이나 그 구역 전체에 동반이 안 될 때만 적는다. "주말, 공휴일은 입장 불가" 는 excludedDays 에 적지만, "주말은 13kg 이하" 나 "실내 매장은 매장에 따라 불가" 는 적지 않는다. "해수욕장 개장 기간에는 동반이 제한될 수 있음" 처럼 그럴 수도 있다는 말도 적지 않는다.
            8. 시설 이름이나 소개 문구의 분위기만으로 칸을 채우지 않는다. 조건을 직접 말한 문장이 있어야 한다.
            9. 근거는 조각 번호로만 답하고 문장을 새로 쓰지 않는다. 근거 조각을 댈 수 없는 칸은 null 로 둔다.

            칸의 뜻
            - scope: ALL_AREA 시설 전체에 동반 가능, PARTIAL 일부 구역만 되거나 일부 구역이 안 됨, NONE 반려동물 동반 불가. 구역을 말할 때만 적는다. "전 견종 동반 가능" 처럼 어떤 개가 되는지만 말하면 null 이다. excludedZones 나 allowedZonesOnly 를 적으면 PARTIAL 이다
            - guideDogOnly: 안내견(보조견)만 들어갈 수 있으면 true, 안내견이 아니어도 된다고 하면 false
            - petOnly: 반려동물과 함께 온 사람만 받는 곳이면 true, 반려동물 없이도 들어갈 수 있다고 하면 false
            - indoorAllowed, outdoorAllowed: 실내, 실외에 반려견과 함께 들어갈 수 있으면 true, 안 되면 false
            - maxWeightKg, weightInclusive: 몸무게 상한과 그 값을 포함하는지. "10kg 이하" 는 10 과 true, "10kg 미만" 은 10 과 false
            - maxCount: 함께 들어갈 수 있는 마릿수 상한. "객실당 최대 2마리" 는 2
            - sizeRule: SMALL_ONLY 소형견만, SMALL_MEDIUM 소형·중형견까지(대형견 불가), ALL 크기 제한 없음
            - breedRule: NONE 견종 제한 없음, DANGEROUS_MUZZLE 맹견은 입마개를 하면 됨, DANGEROUS_BANNED 맹견은 불가. 맹견은 법이 정한 맹견 견종을 말한다. 몸무게나 행동(입질, 공격성, 짖음)에 따른 제한은 breedRule 이 아니다
            - carrierRequired: 목줄만으로는 안 되고 이동장, 가방, 유모차에 넣어야 하면 true, 넣지 않아도 된다고 하면 false
            - leashRequired: 목줄이나 리드줄이 필요하면 true
            - excludedZones: 반려동물이 들어갈 수 없는 구역 이름 (예: 실내, 객실, 수영장)
            - allowedZonesOnly: 반려동물이 들어갈 수 있는 구역이 정해져 있으면 그 구역 이름 (예: 야외 테라스, B구역)
            - excludedDays: 동반이 안 되는 날이나 기간 (예: 주말, 공휴일, 금요일)
            - extraFeeAmount, extraFeeUnit: 추가 요금(원)과 단위. PER_DOG 마리당, PER_NIGHT 1박당, PER_VISIT 1회 입장당. 단위를 말하지 않으면 extraFeeUnit 은 null
            - requiredItems: 챙겨 가거나 착용해야 할 물건 (예: 배변봉투, 매너벨트, 인식표). 입마개는 모든 개에게 요구할 때만 여기에 넣는다. 맹견에게만 요구하면 breedRule 로 적고, 몸무게나 크기로 좁혀 요구하면 어디에도 적지 않는다
            - vaccineProof: 예방접종 증명이 필요하면 true
            - advanceInquiry: 가기 전에 문의나 예약 확인이 필요하다고 하면 true

            답의 모양
            - fields 에 스무 칸을 모두 적는다.
            - evidence 에는 fields 에서 null 이 아닌 칸마다 하나씩 fieldName 과 그 값을 말한 조각 번호(segments)를 적는다. null 인 칸은 evidence 에 넣지 않는다.
            - 구역, 날, 물건 이름은 원문에 쓰인 말 그대로 적는다.
            - 공백과 줄바꿈 없이 한 줄로 답한다.

            예시 — 원문 조각이 다음과 같을 때
            [1] (동반 가능 동물) 5kg 이하 소형견만 동반 가능
            [2] (필요사항) 목줄 착용
            [3] (기타 동반 정보) - 객실당 최대 2마리
            [4] (기타 동반 정보) - 수영장은 동반 불가
            답
            {"fields":{"scope":"PARTIAL","guideDogOnly":null,"petOnly":null,"indoorAllowed":null,"outdoorAllowed":null,"maxWeightKg":5,"weightInclusive":true,"maxCount":2,"sizeRule":"SMALL_ONLY","breedRule":null,"carrierRequired":null,"leashRequired":true,"excludedZones":["수영장"],"allowedZonesOnly":null,"excludedDays":null,"extraFeeAmount":null,"extraFeeUnit":null,"requiredItems":null,"vaccineProof":null,"advanceInquiry":null},"evidence":[{"fieldName":"scope","segments":[4]},{"fieldName":"maxWeightKg","segments":[1]},{"fieldName":"weightInclusive","segments":[1]},{"fieldName":"maxCount","segments":[3]},{"fieldName":"sizeRule","segments":[1]},{"fieldName":"leashRequired","segments":[2]},{"fieldName":"excludedZones","segments":[4]}]}""";

    /**
     * 모델에게 보여 줄 칸 이름입니다.
     *
     * 원문 키(acmpyPsblCpam) 대신 뜻이 드러나는 한국어 이름을 씁니다.
     * 모델이 칸의 뜻을 알아야 "필요사항의 입마개" 와 "기타 정보의 맹견 입마개" 를 가려 읽습니다.
     * 문화정보원 키는 이미 한국어라 그대로 씁니다.
     */
    static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("acmpyPsblCpam", "동반 가능 동물"),
            Map.entry("acmpyNeedMtr", "필요사항"),
            Map.entry("etcAcmpyInfo", "기타 동반 정보"),
            Map.entry("relaAcdntRiskMtr", "사고 대비"),
            Map.entry("facltNm", "시설 이름"),
            Map.entry("lineIntro", "한 줄 소개"),
            Map.entry("intro", "소개"),
            Map.entry("featureNm", "특징"),
            Map.entry("tooltip", "툴팁"),
            Map.entry("sbrsEtc", "부대시설 기타"),
            Map.entry("posblFcltyEtc", "주변 이용 가능 시설 기타"),
            Map.entry("exprnProgrm", "체험 프로그램"),
            Map.entry("clturEvent", "문화 행사"),
            Map.entry("direction", "오시는 길"),
            Map.entry("themaEnvrnCl", "테마 환경"),
            Map.entry("glampInnerFclty", "글램핑 내부 시설"),
            Map.entry("caravInnerFclty", "카라반 내부 시설"));

    private static final Pattern LINE_BREAKS = Pattern.compile("\\R+");

    private static final Map<String, Object> SCHEMA = buildSchema();

    private LlmPrompt() {
    }

    /**
     * 사용자 메시지입니다 — "원문 조각" 한 줄 뒤에 [번호] (칸 이름) 조각.
     *
     * 조각 안의 줄바꿈은 빈칸으로 바꿉니다. 한 조각이 여러 줄로 보이면
     * 모델이 다음 줄을 다른 조각으로 읽을 수 있습니다.
     */
    public static String userMessage(List<Segment> segments) {
        StringBuilder message = new StringBuilder("원문 조각");
        for (Segment segment : segments) {
            message.append('\n')
                    .append('[').append(segment.number()).append("] (")
                    .append(LABELS.getOrDefault(segment.originField(), segment.originField()))
                    .append(") ")
                    .append(LINE_BREAKS.matcher(segment.text()).replaceAll(" "));
        }
        return message.toString();
    }

    public static Map<String, Object> schema() {
        return SCHEMA;
    }

    // 스키마의 모든 객체는 순서를 지키는 맵으로 만듦
    // 키가 둘 이상인 Map.of 는 순회 순서가 JVM 을 띄울 때마다 달라질 수 있어 같은 입력에 다른 요청 글이 나감
    private static Map<String, Object> buildSchema() {
        Map<String, Object> fieldProperties = new LinkedHashMap<>();
        fieldProperties.put(FieldNames.SCOPE, nullable(enumOf("ALL_AREA", "PARTIAL", "NONE")));
        fieldProperties.put(FieldNames.GUIDE_DOG_ONLY, nullable(type("boolean")));
        fieldProperties.put(FieldNames.PET_ONLY, nullable(type("boolean")));
        fieldProperties.put(FieldNames.INDOOR_ALLOWED, nullable(type("boolean")));
        fieldProperties.put(FieldNames.OUTDOOR_ALLOWED, nullable(type("boolean")));
        fieldProperties.put(FieldNames.MAX_WEIGHT_KG, nullable(type("number")));
        fieldProperties.put(FieldNames.WEIGHT_INCLUSIVE, nullable(type("boolean")));
        fieldProperties.put(FieldNames.MAX_COUNT, nullable(type("integer")));
        fieldProperties.put(FieldNames.SIZE_RULE, nullable(enumOf("SMALL_ONLY", "SMALL_MEDIUM", "ALL")));
        fieldProperties.put(FieldNames.BREED_RULE, nullable(enumOf("NONE", "DANGEROUS_MUZZLE", "DANGEROUS_BANNED")));
        fieldProperties.put(FieldNames.CARRIER_REQUIRED, nullable(type("boolean")));
        fieldProperties.put(FieldNames.LEASH_REQUIRED, nullable(type("boolean")));
        fieldProperties.put(FieldNames.EXCLUDED_ZONES, nullable(stringList()));
        fieldProperties.put(FieldNames.ALLOWED_ZONES_ONLY, nullable(stringList()));
        fieldProperties.put(FieldNames.EXCLUDED_DAYS, nullable(stringList()));
        fieldProperties.put(FieldNames.EXTRA_FEE_AMOUNT, nullable(type("integer")));
        fieldProperties.put(FieldNames.EXTRA_FEE_UNIT, nullable(enumOf("PER_DOG", "PER_NIGHT", "PER_VISIT")));
        fieldProperties.put(FieldNames.REQUIRED_ITEMS, nullable(stringList()));
        fieldProperties.put(FieldNames.VACCINE_PROOF, nullable(type("boolean")));
        fieldProperties.put(FieldNames.ADVANCE_INQUIRY, nullable(type("boolean")));

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("type", "object");
        fields.put("properties", Collections.unmodifiableMap(fieldProperties));
        fields.put("required", FieldNames.ALL);
        fields.put("additionalProperties", false);

        Map<String, Object> fieldName = new LinkedHashMap<>();
        fieldName.put("type", "string");
        fieldName.put("enum", FieldNames.ALL);

        Map<String, Object> segmentNumbers = new LinkedHashMap<>();
        segmentNumbers.put("type", "array");
        segmentNumbers.put("items", type("integer"));

        Map<String, Object> citationProperties = new LinkedHashMap<>();
        citationProperties.put("fieldName", Collections.unmodifiableMap(fieldName));
        citationProperties.put("segments", Collections.unmodifiableMap(segmentNumbers));

        Map<String, Object> citation = new LinkedHashMap<>();
        citation.put("type", "object");
        citation.put("properties", Collections.unmodifiableMap(citationProperties));
        citation.put("required", List.of("fieldName", "segments"));
        citation.put("additionalProperties", false);

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("type", "array");
        evidence.put("items", Collections.unmodifiableMap(citation));

        Map<String, Object> rootProperties = new LinkedHashMap<>();
        rootProperties.put("fields", Collections.unmodifiableMap(fields));
        rootProperties.put("evidence", Collections.unmodifiableMap(evidence));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "object");
        root.put("properties", Collections.unmodifiableMap(rootProperties));
        root.put("required", List.of("fields", "evidence"));
        root.put("additionalProperties", false);
        return Collections.unmodifiableMap(root);
    }

    // 값이 없으면 null — 조건 칸에서 null 은 "정보 없음" 임
    private static Map<String, Object> nullable(Map<String, Object> inner) {
        List<Object> anyOf = new ArrayList<>();
        anyOf.add(type("null"));
        anyOf.add(inner);
        return Map.of("anyOf", List.copyOf(anyOf));
    }

    private static Map<String, Object> type(String name) {
        return Map.of("type", name);
    }

    private static Map<String, Object> enumOf(String... values) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        schema.put("enum", List.of(values));
        return Collections.unmodifiableMap(schema);
    }

    private static Map<String, Object> stringList() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("items", type("string"));
        return Collections.unmodifiableMap(schema);
    }
}
