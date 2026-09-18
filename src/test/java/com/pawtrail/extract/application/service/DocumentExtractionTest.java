package com.pawtrail.extract.application.service;

import com.pawtrail.extract.application.support.DocumentOutcome;
import com.pawtrail.extract.application.support.LlmReuse;
import com.pawtrail.extract.domain.enums.ExtractionMethod;
import com.pawtrail.extract.domain.enums.Scope;
import com.pawtrail.extract.domain.enums.SourceType;
import com.pawtrail.extract.domain.exception.LlmDocumentException;
import com.pawtrail.extract.domain.exception.LlmUnavailableException;
import com.pawtrail.extract.domain.model.ConditionFields;
import com.pawtrail.extract.domain.model.FieldNames;
import com.pawtrail.extract.domain.model.LlmAnswer;
import com.pawtrail.extract.domain.model.LlmCitation;
import com.pawtrail.extract.domain.model.PendingDocument;
import com.pawtrail.extract.domain.model.PolicyItem;
import com.pawtrail.extract.domain.model.Segment;
import com.pawtrail.extract.domain.provider.LlmProvider;
import com.pawtrail.extract.infrastructure.provider.convert.CulturePayloadReader;
import com.pawtrail.extract.infrastructure.provider.convert.GoCampingPayloadReader;
import com.pawtrail.extract.infrastructure.provider.convert.PetTourPayloadReader;
import com.pawtrail.extract.support.RawFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 원문 한 건이 항목이 되는 길을 실제 원문 표본과 흉내 모델로 봅니다.
 *
 * 읽기 · 규칙 · 합치기 · 정규화 · 검사는 진짜이고 모델만 흉내입니다.
 * 흉내 모델은 받은 조각에서 문구를 찾아 그 번호를 근거로 답합니다 — 번호를 박아 두면
 * 조각 나누기가 바뀔 때마다 테스트가 깨지기 때문입니다.
 */
class DocumentExtractionTest {

    private static final UUID PLACE_ID = UUID.fromString("01a09015-b6bc-7812-8e7e-d0c59c46b007");

    private ScriptedLlm llm;
    private DocumentExtraction extraction;
    private LlmReuse reuse;

    @BeforeEach
    void setUp() {
        llm = new ScriptedLlm();
        extraction = new DocumentExtraction(
                List.of(new PetTourPayloadReader(), new GoCampingPayloadReader(), new CulturePayloadReader()),
                new LlmExtractor(llm));
        reuse = new LlmReuse();
    }

    @Test
    @DisplayName("고캠핑 불가능 대 본문 7kg 이하 동반 — 가부를 비우고 충돌 한 줄 · 체중은 그대로 · MIXED")
    void 규칙과_모델이_갈림() {
        llm.script = segments -> {
            int number = numberOf(segments, "7kg 이하");
            return new LlmAnswer(ConditionFields.builder()
                    .outdoorAllowed(true).maxWeightKg(new BigDecimal("7")).weightInclusive(true).build(),
                    List.of(new LlmCitation(FieldNames.OUTDOOR_ALLOWED, List.of(number)),
                            new LlmCitation(FieldNames.MAX_WEIGHT_KG, List.of(number)),
                            new LlmCitation(FieldNames.WEIGHT_INCLUSIVE, List.of(number))));
        };

        PolicyItem item = sent(extraction.extract(document(SourceType.GOCAMPING, "gocamping/refused-text"), reuse));

        assertThat(item.fields().scope()).isNull();
        assertThat(item.fields().indoorAllowed()).isNull();
        assertThat(item.fields().outdoorAllowed()).isNull();
        assertThat(item.fields().maxWeightKg()).isEqualByComparingTo("7");
        assertThat(item.conflicts()).hasSize(1);
        assertThat(item.conflicts().getFirst().fieldName()).isEqualTo(FieldNames.SCOPE);
        assertThat(item.conflicts().getFirst().fieldText()).isEqualTo("불가능");
        assertThat(item.conflicts().getFirst().bodyText()).contains("7kg 이하");
        assertThat(item.method()).isEqualTo(ExtractionMethod.MIXED);
        assertThat(item.placeId()).isEqualTo(PLACE_ID);
    }

    @Test
    @DisplayName("동반 구분이 없는 공사 원문에서 모델이 제외 구역만 말하면 범위를 일부 구역으로 채워 보낸다")
    void 정규화가_붙음() {
        llm.script = segments -> {
            int number = numberOf(segments, "레스토랑 입장 불가");
            return new LlmAnswer(ConditionFields.builder().excludedZones(List.of("레스토랑")).build(),
                    List.of(new LlmCitation(FieldNames.EXCLUDED_ZONES, List.of(number))));
        };

        PolicyItem item = sent(extraction.extract(document(SourceType.PET_TOUR, "pet-tour/no-type"), reuse));

        assertThat(item.fields().excludedZones()).containsExactly("레스토랑");
        assertThat(item.fields().scope()).isEqualTo(Scope.PARTIAL);
        // 채운 범위에도 근거 문장이 붙음 — 판정 화면이 칸마다 근거를 보여 줌
        assertThat(item.evidence()).filteredOn(e -> e.fieldName().equals(FieldNames.SCOPE))
                .extracting(e -> e.segmentText()).allMatch(text -> text.contains("레스토랑 입장 불가"));
        assertThat(item.method()).isEqualTo(ExtractionMethod.LLM);
    }

    @Test
    @DisplayName("조건이 하나도 없는 원문은 20칸이 빈 행 · RULE 로 보낸다")
    void 빈_원문() {
        PolicyItem item = sent(extraction.extract(document(SourceType.PET_TOUR, "pet-tour/empty-block"), reuse));

        assertThat(item.fields().isEmpty()).isTrue();
        assertThat(item.evidence()).isEmpty();
        assertThat(item.method()).isEqualTo(ExtractionMethod.RULE);
        assertThat(llm.calls).isZero();
    }

    @Test
    @DisplayName("문화정보원 동물병원은 보내지 않고 처리 완료로 둔다")
    void 동물병원() {
        Map<String, Object> payload = RawFixtures.load("culture/plain");
        @SuppressWarnings("unchecked")
        Map<String, Object> list = new HashMap<>((Map<String, Object>) payload.get("list"));
        list.put("카테고리3", "동물병원");
        Map<String, Object> vet = new HashMap<>(payload);
        vet.put("list", list);

        DocumentOutcome outcome = extraction.extract(document(SourceType.CULTURE_CSV, vet), reuse);

        assertThat(outcome.kind()).isEqualTo(DocumentOutcome.Kind.SKIPPED);
        assertThat(llm.calls).isZero();
    }

    @Test
    @DisplayName("원문에 약속한 키가 없으면 그 원문만 실패로 둔다")
    void 키가_없음() {
        Map<String, Object> payload = new HashMap<>(RawFixtures.load("gocamping/refused"));
        @SuppressWarnings("unchecked")
        Map<String, Object> list = new HashMap<>((Map<String, Object>) payload.get("list"));
        list.remove("animalCmgCl");
        payload.put("list", list);

        DocumentOutcome outcome = extraction.extract(document(SourceType.GOCAMPING, payload), reuse);

        assertThat(outcome.kind()).isEqualTo(DocumentOutcome.Kind.FAILED);
        assertThat(outcome.reason()).contains("animalCmgCl");
    }

    @Test
    @DisplayName("모델 답이 잘리면 그 원문만 실패로 둔다")
    void 모델_답이_잘림() {
        llm.error = new LlmDocumentException(LlmDocumentException.Reason.TRUNCATED, "응답이 잘렸습니다");

        DocumentOutcome outcome = extraction.extract(document(SourceType.GOCAMPING, "gocamping/refused-text"), reuse);

        assertThat(outcome.kind()).isEqualTo(DocumentOutcome.Kind.FAILED);
        assertThat(outcome.reason()).contains("TRUNCATED");
    }

    @Test
    @DisplayName("policy 가 받지 않을 값(0마리)이면 보내지 않고 그 원문만 실패로 둔다")
    void 검사에_걸림() {
        llm.script = segments -> {
            int number = numberOf(segments, "7kg 이하");
            return new LlmAnswer(ConditionFields.builder().maxCount((short) 0).build(),
                    List.of(new LlmCitation(FieldNames.MAX_COUNT, List.of(number))));
        };

        DocumentOutcome outcome = extraction.extract(document(SourceType.GOCAMPING, "gocamping/refused-text"), reuse);

        assertThat(outcome.kind()).isEqualTo(DocumentOutcome.Kind.FAILED);
        assertThat(outcome.reason()).contains("마릿수");
    }

    @Test
    @DisplayName("모델을 부를 수 없으면 실패로 두지 않고 올려 보낸다 — 실행이 멈춰야 함")
    void 모델을_부를_수_없음() {
        llm.error = new LlmUnavailableException("연결 실패", new RuntimeException());

        assertThatThrownBy(() -> extraction.extract(document(SourceType.GOCAMPING, "gocamping/refused-text"), reuse))
                .isInstanceOf(LlmUnavailableException.class);
    }

    private static PolicyItem sent(DocumentOutcome outcome) {
        assertThat(outcome.kind()).as(outcome.reason()).isEqualTo(DocumentOutcome.Kind.SEND);
        return outcome.item();
    }

    private static PendingDocument document(SourceType source, String fixture) {
        return document(source, RawFixtures.load(fixture));
    }

    private static PendingDocument document(SourceType source, Map<String, Object> payload) {
        return new PendingDocument(UUID.randomUUID(), source, "sample", PLACE_ID, payload, "hash");
    }

    private static int numberOf(List<Segment> segments, String phrase) {
        return segments.stream()
                .filter(segment -> segment.text().contains(phrase))
                .map(Segment::number)
                .findFirst()
                .orElseThrow(() -> new AssertionError("조각에 없음: " + phrase));
    }

    // 받은 조각을 보고 답을 만드는 흉내 모델
    private static final class ScriptedLlm implements LlmProvider {

        Function<List<Segment>, LlmAnswer> script = segments -> new LlmAnswer(ConditionFields.empty(), List.of());
        RuntimeException error;
        int calls;

        @Override
        public LlmAnswer read(List<Segment> segments) {
            calls++;
            if (error != null) {
                throw error;
            }
            return script.apply(segments);
        }

        @Override
        public String modelName() {
            return "scripted";
        }

        @Override
        public String promptVersion() {
            return "v2";
        }
    }
}
