package com.pawtrail.extract.eval;

import com.pawtrail.extract.domain.enums.SourceType;
import com.pawtrail.extract.domain.model.Segment;
import com.pawtrail.extract.domain.provider.PayloadReader;
import com.pawtrail.extract.domain.rule.ConditionRules;
import com.pawtrail.extract.domain.rule.Segmenter;
import com.pawtrail.extract.infrastructure.provider.convert.CulturePayloadReader;
import com.pawtrail.extract.infrastructure.provider.convert.GoCampingPayloadReader;
import com.pawtrail.extract.infrastructure.provider.convert.PetTourPayloadReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정답 파일이 모양을 지키는지 봅니다. 모델을 부르지 않아 기본 빌드에서 돕니다.
 *
 * 판정으로 정답을 고치다 번호나 칸 이름이 어긋나면, 평가를 돌리기 전에 여기서 걸립니다.
 */
class EvalAnswersTest {

    private final Map<SourceType, PayloadReader> readers = Map.of(
            SourceType.PET_TOUR, new PetTourPayloadReader(),
            SourceType.GOCAMPING, new GoCampingPayloadReader(),
            SourceType.CULTURE_CSV, new CulturePayloadReader());

    @Test
    @DisplayName("평가 표본 100건 · 공사 40 · 고캠핑 30 · 문화정보원 30")
    void 평가_표본_수() {
        assertCounts(EvalAnswers.load("answers"), 40, 30, 30);
    }

    @Test
    @DisplayName("새 표본 40건 · 공사 16 · 고캠핑 12 · 문화정보원 12")
    void 새_표본_수() {
        assertCounts(EvalAnswers.load("holdout"), 16, 12, 12);
    }

    @Test
    @DisplayName("새 표본은 평가 표본과 원문이 하나도 겹치지 않는다")
    void 묶음_사이_겹침() {
        Set<String> evaluated = EvalAnswers.load("answers").stream().map(EvalSample::id).collect(Collectors.toSet());
        Set<String> holdout = new HashSet<>(EvalAnswers.load("holdout").stream().map(EvalSample::id).toList());

        holdout.retainAll(evaluated);
        assertThat(holdout).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"answers", "holdout"})
    @DisplayName("값이 있는 칸마다 근거가 있고, 근거 번호는 그 표본의 조각 안에 있다")
    void 근거_번호(String set) {
        for (EvalSample sample : EvalAnswers.load(set)) {
            List<Segment> segments = Segmenter.split(ConditionRules.extract(
                    readers.get(SourceType.valueOf(sample.source())).read(EvalAnswers.payload(sample.id()))).texts());
            List<String> filled = sample.fields().entrySet().stream()
                    .filter(entry -> Objects.nonNull(entry.getValue()))
                    .map(Map.Entry::getKey).toList();

            assertThat(segments).as("%s 표본 %d 은 LLM 으로 가는 원문이어야 함", set, sample.no()).isNotEmpty();
            assertThat(sample.evidence().keySet()).as("%s 표본 %d 의 근거 칸", set, sample.no())
                    .containsExactlyInAnyOrderElementsOf(filled);
            sample.evidence().forEach((field, numbers) -> assertThat(numbers)
                    .as("%s 표본 %d 의 %s 근거 번호", set, sample.no(), field)
                    .isNotEmpty()
                    .allSatisfy(number -> assertThat(number).isBetween(1, segments.size())));
        }
    }

    private static void assertCounts(List<EvalSample> samples, long petTour, long goCamping, long culture) {
        assertThat(samples).hasSize((int) (petTour + goCamping + culture));
        assertThat(samples.stream().collect(Collectors.groupingBy(EvalSample::source, Collectors.counting())))
                .containsEntry("PET_TOUR", petTour)
                .containsEntry("GOCAMPING", goCamping)
                .containsEntry("CULTURE_CSV", culture);
        assertThat(samples).extracting(EvalSample::id).doesNotHaveDuplicates();
        assertThat(samples).extracting(EvalSample::no)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, samples.size()).boxed().toList());
    }
}
