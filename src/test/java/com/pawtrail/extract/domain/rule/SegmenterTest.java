package com.pawtrail.extract.domain.rule;

import com.pawtrail.extract.domain.model.Segment;
import com.pawtrail.extract.domain.model.SourceText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 조각 나누기 규칙을 고정합니다.
 *
 * 글은 2026.9.13 원문 덤프에서 옮긴 실물입니다. 프롬프트를 다듬은 시험(실물 표본 28건)도
 * 이 규칙으로 나눈 조각을 썼습니다.
 */
class SegmenterTest {

    @Test
    @DisplayName("공사 네 칸 — 동반 가능 동물은 통째, 필요사항은 쉼표, 기타는 줄바꿈 (익선동 한옥거리)")
    void 공사_익선동() {
        List<Segment> segments = Segmenter.split(List.of(
                new SourceText("acmpyPsblCpam", "전 견종 동반 가능"),
                new SourceText("acmpyNeedMtr", "목줄 착용"),
                new SourceText("etcAcmpyInfo",
                        "- 각 가게 개별 정책 문의 필요\n- 맹견의 경우, 입마개 착용 필수\n- 배변봉투 지참 및 배변처리 필수")));

        assertThat(segments).containsExactly(
                new Segment(1, "acmpyPsblCpam", null, "전 견종 동반 가능"),
                new Segment(2, "acmpyNeedMtr", 1, "목줄 착용"),
                new Segment(3, "etcAcmpyInfo", 1, "- 각 가게 개별 정책 문의 필요"),
                new Segment(4, "etcAcmpyInfo", 2, "- 맹견의 경우, 입마개 착용 필수"),
                new Segment(5, "etcAcmpyInfo", 3, "- 배변봉투 지참 및 배변처리 필수"));
    }

    @Test
    @DisplayName("줄바꿈 없이 붙인 글머리도 나눈다 — 기타 901건 중 413건이 이 모양")
    void 붙은_글머리() {
        List<String> texts = texts(Segmenter.split(List.of(new SourceText("etcAcmpyInfo",
                "- 실내는 동반불가- 맹견의 경우, 입마개 착용 필수- 배변봉투 지참 및 배변처리 필수"))));

        assertThat(texts).containsExactly(
                "- 실내는 동반불가", "- 맹견의 경우, 입마개 착용 필수", "- 배변봉투 지참 및 배변처리 필수");
    }

    @Test
    @DisplayName("괄호 뒤에 붙은 글머리도 경계다 · 공백 없는 하이픈은 나누지 않는다")
    void 괄호_뒤_글머리와_하이픈() {
        List<String> texts = texts(Segmenter.split(List.of(new SourceText("relaAcdntRiskMtr",
                "산책로 이용(목줄 필수)- 대형견-소형견 구역 분리"))));

        assertThat(texts).containsExactly("산책로 이용(목줄 필수)", "- 대형견-소형견 구역 분리");
    }

    @Test
    @DisplayName("\" / \" 로 이어 쓴 조건도 나눈다")
    void 빗금() {
        List<String> texts = texts(Segmenter.split(List.of(new SourceText("etcAcmpyInfo",
                "B구역(13~19)만 반려동물 출입 가능합니다. / 반려견 인식표 및 리드 줄, 입마개 필수입니다."))));

        assertThat(texts).containsExactly(
                "B구역(13~19)만 반려동물 출입 가능합니다.", "반려견 인식표 및 리드 줄, 입마개 필수입니다.");
    }

    @Test
    @DisplayName("글머리표 하나뿐인 줄과 빈 조각은 버린다")
    void 빈_조각() {
        List<String> texts = texts(Segmenter.split(List.of(new SourceText("etcAcmpyInfo", "- A\n-\n\n- B  "))));

        assertThat(texts).containsExactly("- A", "- B");
    }

    @Test
    @DisplayName("필요사항은 쉼표로 나눈다")
    void 쉼표() {
        List<Segment> segments = Segmenter.split(List.of(
                new SourceText("acmpyNeedMtr", "입마개 착용,목줄 착용,매너벨트 착용")));

        assertThat(texts(segments)).containsExactly("입마개 착용", "목줄 착용", "매너벨트 착용");
        assertThat(segments).extracting(Segment::indexInField).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("고캠핑 글 칸은 문장 끝과 줄바꿈으로 나눈다 (벳소캠프)")
    void 고캠핑_문장() {
        List<String> texts = texts(Segmenter.split(List.of(new SourceText("intro",
                "데크가 비교적 넓고 사이트마다 개별 개수대가 설치되어 있어 편리하다. 7kg 이하의 반려견은 동반 가능하다.\n펜션과 함께 운영한다"))));

        assertThat(texts).containsExactly(
                "데크가 비교적 넓고 사이트마다 개별 개수대가 설치되어 있어 편리하다.",
                "7kg 이하의 반려견은 동반 가능하다.",
                "펜션과 함께 운영한다");
    }

    @Test
    @DisplayName("문화정보원 칸은 쉼표가 있어도 통째 한 조각이고 칸 안 순서를 비운다")
    void 문화정보원_통째() {
        List<Segment> segments = Segmenter.split(List.of(
                new SourceText("반려동물 제한사항", "야외만 반려동물 동반 가능, 목줄")));

        assertThat(segments).containsExactly(new Segment(1, "반려동물 제한사항", null, "야외만 반려동물 동반 가능, 목줄"));
    }

    @Test
    @DisplayName("번호는 칸을 넘어 문서 전체에서 이어 붙는다")
    void 문서_전체_번호() {
        List<Segment> segments = Segmenter.split(List.of(
                new SourceText("입장 가능 동물 크기", "주말 및 공휴일은 13kg 이하"),
                new SourceText("애견 동반 추가 요금", "5,000~6,000원"),
                new SourceText("반려동물 제한사항", "목줄, 배변봉투")));

        assertThat(segments).extracting(Segment::number).containsExactly(1, 2, 3);
        assertThat(segments).extracting(Segment::indexInField).containsOnlyNulls();
    }

    @Test
    @DisplayName("같은 원문은 늘 같은 조각 · 같은 번호다")
    void 같은_입력_같은_조각() {
        List<SourceText> texts = List.of(
                new SourceText("acmpyNeedMtr", "입마개 착용,목줄 착용"),
                new SourceText("etcAcmpyInfo", "- 맹견의 경우, 입마개 착용 필수- 배변봉투 지참"));

        assertThat(Segmenter.split(texts)).isEqualTo(Segmenter.split(texts));
    }

    private static List<String> texts(List<Segment> segments) {
        return segments.stream().map(Segment::text).toList();
    }
}
