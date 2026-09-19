package com.pawtrail.extract.domain.rule;

import com.pawtrail.extract.domain.enums.BreedRule;
import com.pawtrail.extract.domain.enums.SizeRule;
import com.pawtrail.extract.domain.model.ConditionFields;
import com.pawtrail.extract.domain.model.Evidence;
import com.pawtrail.extract.domain.model.LlmAnswer;
import com.pawtrail.extract.domain.model.LlmCitation;
import com.pawtrail.extract.domain.model.LlmReading;
import com.pawtrail.extract.domain.model.Segment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CitationCheckTest {

    // 익선동 한옥거리 조각
    private static final List<Segment> SEGMENTS = List.of(
            new Segment(1, "acmpyPsblCpam", null, "전 견종 동반 가능"),
            new Segment(2, "acmpyNeedMtr", 1, "목줄 착용"),
            new Segment(3, "etcAcmpyInfo", 1, "- 각 가게 개별 정책 문의 필요"),
            new Segment(4, "etcAcmpyInfo", 2, "- 맹견의 경우, 입마개 착용 필수"),
            new Segment(5, "etcAcmpyInfo", 3, "- 배변봉투 지참 및 배변처리 필수"));

    @Test
    @DisplayName("근거 번호를 원문 조각으로 바꾼다 — 문구 · 원문 키 · 칸 안 순서가 조각에서 온다")
    void 근거_바꾸기() {
        LlmReading reading = CitationCheck.check(new LlmAnswer(
                ConditionFields.builder().sizeRule(SizeRule.ALL).breedRule(BreedRule.DANGEROUS_MUZZLE).build(),
                List.of(new LlmCitation("sizeRule", List.of(1)), new LlmCitation("breedRule", List.of(4)))),
                SEGMENTS);

        assertThat(reading.fields().sizeRule()).isEqualTo(SizeRule.ALL);
        assertThat(reading.evidence()).containsExactly(
                Evidence.ofLlm("sizeRule", "acmpyPsblCpam", null, "전 견종 동반 가능"),
                Evidence.ofLlm("breedRule", "etcAcmpyInfo", 2, "- 맹견의 경우, 입마개 착용 필수"));
        assertThat(reading.droppedValues()).isZero();
        assertThat(reading.ignoredCitations()).isZero();
        assertThat(reading.droppedNumbers()).isZero();
    }

    @Test
    @DisplayName("근거 없는 값은 버리고 센다 — 시험 3 에서 지어낸 예방접종 증명")
    void 근거_없는_값() {
        LlmReading reading = CitationCheck.check(new LlmAnswer(
                ConditionFields.builder().leashRequired(true).vaccineProof(true).build(),
                List.of(new LlmCitation("leashRequired", List.of(2)))),
                SEGMENTS);

        assertThat(reading.fields().leashRequired()).isTrue();
        assertThat(reading.fields().vaccineProof()).isNull();
        assertThat(reading.droppedValues()).isEqualTo(1);
    }

    @Test
    @DisplayName("비어 있는 칸이나 조건 칸이 아닌 이름을 가리키는 근거는 무시한다")
    void 무시할_근거() {
        LlmReading reading = CitationCheck.check(new LlmAnswer(
                ConditionFields.builder().leashRequired(true).build(),
                List.of(new LlmCitation("leashRequired", List.of(2)),
                        new LlmCitation("extraFeeUnit", List.of(1)),
                        new LlmCitation("parking", List.of(3)))),
                SEGMENTS);

        assertThat(reading.evidence()).hasSize(1);
        assertThat(reading.ignoredCitations()).isEqualTo(2);
    }

    @Test
    @DisplayName("범위 밖 번호는 그 번호만 버리고, 남는 번호가 없으면 그 칸을 비운다")
    void 범위_밖_번호() {
        LlmReading reading = CitationCheck.check(new LlmAnswer(
                ConditionFields.builder().leashRequired(true).advanceInquiry(true).build(),
                List.of(new LlmCitation("leashRequired", List.of(2, 9)),
                        new LlmCitation("advanceInquiry", List.of(0, 6)))),
                SEGMENTS);

        assertThat(reading.fields().leashRequired()).isTrue();
        assertThat(reading.fields().advanceInquiry()).isNull();
        assertThat(reading.droppedNumbers()).isEqualTo(3);
        assertThat(reading.droppedValues()).isEqualTo(1);
    }

    @Test
    @DisplayName("한 칸의 근거가 여러 줄이면 합치고 같은 번호는 한 번만, 번호 순서대로")
    void 근거_합치기() {
        LlmReading reading = CitationCheck.check(new LlmAnswer(
                ConditionFields.builder().sizeRule(SizeRule.ALL).build(),
                List.of(new LlmCitation("sizeRule", List.of(4, 1)),
                        new LlmCitation("sizeRule", List.of(1)))),
                SEGMENTS);

        assertThat(reading.evidence()).extracting(Evidence::segmentText)
                .containsExactly("전 견종 동반 가능", "- 맹견의 경우, 입마개 착용 필수");
    }

    @Test
    @DisplayName("빈 목록은 정보 없음으로 보고 셈에 넣지 않는다")
    void 빈_목록() {
        LlmReading reading = CitationCheck.check(new LlmAnswer(
                ConditionFields.builder().requiredItems(List.of()).build(),
                List.of()),
                SEGMENTS);

        assertThat(reading.fields().requiredItems()).isNull();
        assertThat(reading.fields().isEmpty()).isTrue();
        assertThat(reading.droppedValues()).isZero();
    }

    @Test
    @DisplayName("모델이 아무것도 못 찾으면 빈 결과다")
    void 빈_답() {
        LlmReading reading = CitationCheck.check(new LlmAnswer(ConditionFields.empty(), List.of()), SEGMENTS);

        assertThat(reading.fields().isEmpty()).isTrue();
        assertThat(reading.evidence()).isEmpty();
    }
}
