package com.pawtrail.extract.eval;

import com.pawtrail.extract.domain.enums.Scope;
import com.pawtrail.extract.domain.enums.SizeRule;
import com.pawtrail.extract.eval.EvalScorer.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 채점 규칙을 고정합니다. 모델을 부르지 않아 기본 빌드에서 돕니다.
 */
class EvalScorerTest {

    @Test
    @DisplayName("네 경우와 둘 다 빔을 가른다")
    void 경우() {
        assertThat(EvalScorer.judge(null, null)).isEqualTo(Verdict.BOTH_EMPTY);
        assertThat(EvalScorer.judge(null, true)).isEqualTo(Verdict.FABRICATED);
        assertThat(EvalScorer.judge(true, null)).isEqualTo(Verdict.MISSED);
        assertThat(EvalScorer.judge(true, true)).isEqualTo(Verdict.MATCH);
        assertThat(EvalScorer.judge(true, false)).isEqualTo(Verdict.DIFFERENT);
    }

    @Test
    @DisplayName("빈 목록은 정보 없음과 같다")
    void 빈_목록() {
        assertThat(EvalScorer.judge(null, List.of())).isEqualTo(Verdict.BOTH_EMPTY);
        assertThat(EvalScorer.judge(List.of("배변봉투"), List.of())).isEqualTo(Verdict.MISSED);
    }

    @Test
    @DisplayName("숫자는 값으로 견준다 — 10 과 10.0 · 3 과 (short) 3")
    void 숫자() {
        assertThat(EvalScorer.judge(10, new BigDecimal("10.0"))).isEqualTo(Verdict.MATCH);
        assertThat(EvalScorer.judge(3, (short) 3)).isEqualTo(Verdict.MATCH);
        assertThat(EvalScorer.judge(7.5, new BigDecimal("7"))).isEqualTo(Verdict.DIFFERENT);
    }

    @Test
    @DisplayName("enum 은 이름으로 견준다")
    void 이름() {
        assertThat(EvalScorer.judge("ALL", SizeRule.ALL)).isEqualTo(Verdict.MATCH);
        assertThat(EvalScorer.judge("PARTIAL", Scope.ALL_AREA)).isEqualTo(Verdict.DIFFERENT);
    }

    @Test
    @DisplayName("목록은 순서만 무시하고, 항목을 더하거나 빠뜨리면 다름이다")
    void 목록() {
        assertThat(EvalScorer.judge(List.of("입마개", "배변봉투"), List.of("배변봉투", "입마개"))).isEqualTo(Verdict.MATCH);
        assertThat(EvalScorer.judge(List.of("배변봉투"), List.of("입마개", "배변봉투"))).isEqualTo(Verdict.DIFFERENT);
        assertThat(EvalScorer.judge(List.of("입마개", "배변봉투"), List.of("배변봉투"))).isEqualTo(Verdict.DIFFERENT);
        assertThat(EvalScorer.judge(List.of("배변봉투"), List.of("배변 봉투"))).isEqualTo(Verdict.DIFFERENT);
    }

    @Test
    @DisplayName("정답 항목이 표기 목록이면 그중 하나면 맞다")
    void 받아_줄_표기() {
        List<Object> gold = List.of(List.of("수목원 내 카페 실내 좌석", "카페 실내 좌석"));

        assertThat(EvalScorer.judge(gold, List.of("카페 실내 좌석"))).isEqualTo(Verdict.MATCH);
        assertThat(EvalScorer.judge(gold, List.of("수목원 내 카페 실내 좌석"))).isEqualTo(Verdict.MATCH);
        assertThat(EvalScorer.judge(gold, List.of("카페"))).isEqualTo(Verdict.DIFFERENT);
    }

    @Test
    @DisplayName("표기 목록 항목과 한 표기 항목이 섞여도 항목마다 따로 맞춘다")
    void 섞인_목록() {
        List<Object> gold = List.of(List.of("1층"), List.of("실외", "야외"));

        assertThat(EvalScorer.judge(gold, List.of("야외", "1층"))).isEqualTo(Verdict.MATCH);
        assertThat(EvalScorer.judge(gold, List.of("실외", "야외"))).isEqualTo(Verdict.DIFFERENT);
    }
}
