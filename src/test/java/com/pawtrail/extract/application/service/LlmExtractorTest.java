package com.pawtrail.extract.application.service;

import com.pawtrail.extract.application.support.LlmReuse;
import com.pawtrail.extract.domain.enums.SizeRule;
import com.pawtrail.extract.domain.exception.LlmDocumentException;
import com.pawtrail.extract.domain.model.ConditionFields;
import com.pawtrail.extract.domain.model.Evidence;
import com.pawtrail.extract.domain.model.LlmAnswer;
import com.pawtrail.extract.domain.model.LlmCitation;
import com.pawtrail.extract.domain.model.LlmReading;
import com.pawtrail.extract.domain.model.Segment;
import com.pawtrail.extract.domain.model.SourceText;
import com.pawtrail.extract.domain.provider.LlmProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmExtractorTest {

    private final FakeProvider provider = new FakeProvider();
    private final LlmExtractor extractor = new LlmExtractor(provider);

    @Test
    @DisplayName("조각을 나눠 모델에 보내고, 근거를 검사해 원문 근거로 돌려준다")
    void 흐름() {
        provider.answer = new LlmAnswer(
                ConditionFields.builder().sizeRule(SizeRule.ALL).vaccineProof(true).build(),
                List.of(new LlmCitation("sizeRule", List.of(1))));

        LlmReading reading = extractor.extract(List.of(
                new SourceText("acmpyPsblCpam", "전 견종 동반 가능"),
                new SourceText("acmpyNeedMtr", "목줄 착용")), new LlmReuse());

        assertThat(provider.received).hasSize(1);
        assertThat(provider.received.get(0)).extracting(Segment::text).containsExactly("전 견종 동반 가능", "목줄 착용");
        assertThat(reading.fields().sizeRule()).isEqualTo(SizeRule.ALL);
        assertThat(reading.fields().vaccineProof()).isNull();
        assertThat(reading.droppedValues()).isEqualTo(1);
        assertThat(reading.evidence()).containsExactly(Evidence.ofLlm("sizeRule", "acmpyPsblCpam", null, "전 견종 동반 가능"));
    }

    @Test
    @DisplayName("한 실행 안에서 같은 입력이면 모델을 다시 부르지 않는다")
    void 재사용() {
        provider.answer = new LlmAnswer(ConditionFields.empty(), List.of());
        LlmReuse reuse = new LlmReuse();
        List<SourceText> texts = List.of(new SourceText("반려동물 제한사항", "목줄, 배변봉투"));

        extractor.extract(texts, reuse);
        extractor.extract(texts, reuse);
        extractor.extract(List.of(new SourceText("반려동물 제한사항", "케이지 이용")), reuse);

        assertThat(provider.received).hasSize(2);
        assertThat(reuse.calls()).isEqualTo(2);
        assertThat(reuse.reused()).isEqualTo(1);
    }

    @Test
    @DisplayName("실행이 바뀌면 다시 부른다 — 재사용은 한 실행 안에서만")
    void 실행마다_새로() {
        provider.answer = new LlmAnswer(ConditionFields.empty(), List.of());
        List<SourceText> texts = List.of(new SourceText("반려동물 제한사항", "목줄, 배변봉투"));

        extractor.extract(texts, new LlmReuse());
        extractor.extract(texts, new LlmReuse());

        assertThat(provider.received).hasSize(2);
    }

    @Test
    @DisplayName("실패한 호출도 센다 — 실패한 답은 담지 않아 같은 입력이 오면 다시 부르고 다시 센다")
    void 실패한_호출() {
        provider.failure = new LlmDocumentException(LlmDocumentException.Reason.TRUNCATED, "잘림");
        LlmReuse reuse = new LlmReuse();
        List<SourceText> texts = List.of(new SourceText("반려동물 제한사항", "목줄, 배변봉투"));

        assertThatThrownBy(() -> extractor.extract(texts, reuse)).isInstanceOf(LlmDocumentException.class);
        assertThatThrownBy(() -> extractor.extract(texts, reuse)).isInstanceOf(LlmDocumentException.class);

        assertThat(reuse.calls()).isEqualTo(2);
        assertThat(reuse.reused()).isZero();
    }

    @Test
    @DisplayName("넘길 조각이 없으면 모델을 부르지 않는다")
    void 조각_없음() {
        LlmReading reading = extractor.extract(List.of(new SourceText("acmpyNeedMtr", " , ")), new LlmReuse());

        assertThat(provider.received).isEmpty();
        assertThat(reading.fields().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("두 번 읽도록 설정되면 두 읽기를 각각 근거 검사한 뒤 안전 쪽으로 합친다")
    void 두_번_읽기() {
        provider.answer = new LlmAnswer(
                ConditionFields.builder().sizeRule(SizeRule.ALL).leashRequired(true).build(),
                List.of(new LlmCitation("sizeRule", List.of(1)), new LlmCitation("leashRequired", List.of(2))));
        provider.second = new LlmAnswer(
                ConditionFields.builder().sizeRule(SizeRule.SMALL_ONLY).build(),
                List.of(new LlmCitation("sizeRule", List.of(1))));

        LlmReading reading = extractor.extract(List.of(
                new SourceText("acmpyPsblCpam", "소형견 동반 가능"),
                new SourceText("acmpyNeedMtr", "목줄 착용")), new LlmReuse());

        assertThat(provider.received).hasSize(1);
        assertThat(provider.secondReceived).hasSize(1);
        // 크기는 좁은 쪽 · 목줄은 한쪽만 필요라 해도 필요
        assertThat(reading.fields().sizeRule()).isEqualTo(SizeRule.SMALL_ONLY);
        assertThat(reading.fields().leashRequired()).isTrue();
    }

    @Test
    @DisplayName("두 번째 읽기만 실패해도 그 문서는 실패다 — 한 번 읽기로 조용히 보내지 않는다")
    void 두_번째_읽기_실패() {
        provider.answer = new LlmAnswer(ConditionFields.empty(), List.of());
        provider.secondFailure = new LlmDocumentException(LlmDocumentException.Reason.TRUNCATED, "잘림");

        assertThatThrownBy(() -> extractor.extract(
                List.of(new SourceText("반려동물 제한사항", "목줄, 배변봉투")), new LlmReuse()))
                .isInstanceOf(LlmDocumentException.class);
    }

    @Test
    @DisplayName("재사용은 합친 결과를 담아, 같은 입력이면 두 읽기 모두 다시 부르지 않는다")
    void 두_번_읽기_재사용() {
        provider.answer = new LlmAnswer(ConditionFields.empty(), List.of());
        provider.second = new LlmAnswer(ConditionFields.empty(), List.of());
        LlmReuse reuse = new LlmReuse();
        List<SourceText> texts = List.of(new SourceText("반려동물 제한사항", "목줄, 배변봉투"));

        extractor.extract(texts, reuse);
        extractor.extract(texts, reuse);

        assertThat(provider.received).hasSize(1);
        assertThat(provider.secondReceived).hasSize(1);
        assertThat(reuse.calls()).isEqualTo(1);
        assertThat(reuse.reused()).isEqualTo(1);
    }

    @Test
    @DisplayName("추출 기록에 남길 모델 이름과 프롬프트 판을 구현에서 가져온다")
    void 기록_값() {
        assertThat(extractor.modelName()).isEqualTo("fake-model");
        assertThat(extractor.promptVersion()).isEqualTo("v1");
    }

    private static class FakeProvider implements LlmProvider {

        private final List<List<Segment>> received = new ArrayList<>();
        private final List<List<Segment>> secondReceived = new ArrayList<>();
        private LlmAnswer answer = new LlmAnswer(ConditionFields.empty(), List.of());
        private RuntimeException failure;
        // 두 번째 읽기 — 비어 있으면 한 번만 읽는 설정
        private LlmAnswer second;
        private RuntimeException secondFailure;

        @Override
        public LlmAnswer read(List<Segment> segments) {
            received.add(segments);
            if (failure != null) {
                throw failure;
            }
            return answer;
        }

        @Override
        public Optional<LlmAnswer> readSecond(List<Segment> segments) {
            if (second == null && secondFailure == null) {
                return Optional.empty();
            }
            secondReceived.add(segments);
            if (secondFailure != null) {
                throw secondFailure;
            }
            return Optional.of(second);
        }

        @Override
        public String modelName() {
            return "fake-model";
        }

        @Override
        public String promptVersion() {
            return "v1";
        }
    }
}
