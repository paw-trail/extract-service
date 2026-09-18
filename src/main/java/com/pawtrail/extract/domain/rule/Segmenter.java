package com.pawtrail.extract.domain.rule;

import com.pawtrail.extract.domain.model.GoCampingRaw;
import com.pawtrail.extract.domain.model.PetTourRaw;
import com.pawtrail.extract.domain.model.Segment;
import com.pawtrail.extract.domain.model.SourceText;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 규칙 추출이 넘긴 원문 칸을 조각으로 나누고 번호를 붙입니다.
 *
 * 모델은 근거를 이 번호로만 답합니다. 그래서 조각은 근거로 보여 줄 만한 크기여야 하고,
 * 같은 원문이면 늘 같은 조각 · 같은 번호가 나와야 같은 입력이 같은 답을 받습니다.
 *
 * <b>원문 칸마다 나누는 법이 다릅니다.</b>
 * <pre>
 * 공사 필요사항                  쉼표 — 고정 낱말을 쉼표로 이은 칸
 * 공사 기타 동반 정보 · 사고 대비   줄바꿈 · 공백 아닌 글자 뒤에 붙은 "- " · " / "
 * 고캠핑 글 칸                   문장 끝 · 줄바꿈
 * 그 밖 (공사 동반 가능 동물 · 문화정보원 칸)   통째 한 조각
 * </pre>
 * 기타 동반 정보 901건 중 413건이 줄바꿈 없이 "- A- B" 로 글머리를 붙여 씁니다.
 * 줄바꿈으로만 나누면 그 413건이 한 조각이 되어 근거가 뭉개집니다.
 * " / " 로 조건을 이어 쓴 칸도 1,432칸 중 22칸 있습니다.
 *
 * <b>번호는 문서 전체에서 1부터 이어 붙입니다.</b>
 * 근거의 segmentIndex 에는 그 칸 안에서 몇 번째인지를 1부터 적고, 통째로 두는 칸은 비웁니다.
 * policy 가 받는 뜻("그 필드 안에서 몇 번째 조각 · 쪼갤 것이 없으면 비움")과 같습니다.
 */
public final class Segmenter {

    static final Set<String> COMMA_FIELDS = Set.of(PetTourRaw.KEY_NEED);
    static final Set<String> BULLET_FIELDS = Set.of(PetTourRaw.KEY_ETC, PetTourRaw.KEY_RISK);
    static final Set<String> SENTENCE_FIELDS = Set.copyOf(GoCampingRaw.TEXT_KEYS);

    private static final Pattern LINES = Pattern.compile("\\R+");
    private static final Pattern SLASH = Pattern.compile(" / ");
    private static final Pattern GLUED_BULLET = Pattern.compile("(?<=\\S)(?=- )");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern COMMA = Pattern.compile(",");

    private Segmenter() {
    }

    public static List<Segment> split(List<SourceText> texts) {
        List<Segment> segments = new ArrayList<>();
        for (SourceText source : texts) {
            boolean whole = isWhole(source.originField());
            List<String> pieces = pieces(source);
            for (int i = 0; i < pieces.size(); i++) {
                Integer indexInField = whole ? null : i + 1;
                segments.add(new Segment(segments.size() + 1, source.originField(), indexInField, pieces.get(i)));
            }
        }
        return List.copyOf(segments);
    }

    static boolean isWhole(String originField) {
        return !COMMA_FIELDS.contains(originField)
                && !BULLET_FIELDS.contains(originField)
                && !SENTENCE_FIELDS.contains(originField);
    }

    static List<String> pieces(SourceText source) {
        String field = source.originField();
        String text = source.text();
        List<String> pieces = new ArrayList<>();
        if (COMMA_FIELDS.contains(field)) {
            pieces.addAll(List.of(COMMA.split(text)));
        } else if (BULLET_FIELDS.contains(field)) {
            for (String line : LINES.split(text)) {
                for (String part : SLASH.split(line)) {
                    pieces.addAll(List.of(GLUED_BULLET.split(part)));
                }
            }
        } else if (SENTENCE_FIELDS.contains(field)) {
            for (String line : LINES.split(text)) {
                pieces.addAll(List.of(SENTENCE_END.split(line)));
            }
        } else {
            pieces.add(text);
        }
        return clean(pieces);
    }

    // 앞뒤 공백을 걷고, 비었거나 글머리표 "-" 하나뿐인 조각은 버림
    private static List<String> clean(List<String> pieces) {
        List<String> cleaned = new ArrayList<>();
        for (String piece : pieces) {
            String stripped = piece.strip();
            if (!stripped.isEmpty() && !stripped.equals("-")) {
                cleaned.add(stripped);
            }
        }
        return cleaned;
    }
}
