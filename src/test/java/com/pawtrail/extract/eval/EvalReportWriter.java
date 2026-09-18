package com.pawtrail.extract.eval;

import com.pawtrail.extract.application.support.LlmReuse;
import com.pawtrail.extract.domain.model.FieldNames;
import com.pawtrail.extract.eval.EvalScore.Disagreement;
import com.pawtrail.extract.eval.EvalScore.SampleResult;
import com.pawtrail.extract.eval.EvalScorer.Verdict;
import com.pawtrail.extract.infrastructure.provider.external.LlmPrompt;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 채점 결과를 보고서로 남깁니다 — 사람이 읽는 Markdown 하나와 다시 계산할 때 쓰는 JSON 하나.
 *
 * <pre>
 * 칸별 표          맞음 · 다름 · 없는 걸 채움 · 있는 걸 놓침 · 둘 다 빔 · 정밀도 · 재현율
 * 근거 적중        값이 맞은 칸 중 정답 조각과 겹친 칸
 * 문서 탓 실패      표본 · 까닭
 * 갈린 칸          판정할 칸 — 원문 조각과 함께
 * 무작위 10문서     정답과 모델이 같았던 칸 — 둘이 같이 틀린 칸을 사람이 보려는 것
 * </pre>
 *
 * 정밀도는 모델이 채운 값 가운데 맞은 비율이고(지어내기를 봄), 재현율은 정답 값 가운데 모델이 맞힌 비율입니다(놓침을 봄).
 */
final class EvalReportWriter {

    static final long RANDOM_SEED = 20260918L;
    static final int RANDOM_SAMPLES = 10;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private EvalReportWriter() {
    }

    // 보고서 이름에 모델 · 프롬프트 판 · 표본 묶음을 모두 넣음 — 판이나 묶음을 바꿔 돌린 보고서가 서로 덮어쓰지 않게
    static Path write(Path dir, String modelName, String promptVersion, String set,
                      EvalScore score, LlmReuse reuse, Duration took) {
        String name = modelName.replaceAll("[^A-Za-z0-9._-]", "_") + "-" + promptVersion + "-" + set;
        try {
            Files.createDirectories(dir);
            Path markdown = dir.resolve(name + ".md");
            Files.writeString(markdown, markdown(modelName, promptVersion, set, score, reuse, took), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve(name + ".json"), json(modelName, promptVersion, set, score), StandardCharsets.UTF_8);
            return markdown;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String summary(EvalScore score) {
        int match = score.total(Verdict.MATCH);
        int different = score.total(Verdict.DIFFERENT);
        int fabricated = score.total(Verdict.FABRICATED);
        int missed = score.total(Verdict.MISSED);
        return "채점 " + score.scored() + "건 · 문서 탓 실패 " + score.failures().size() + "건"
                + " · 맞음 " + match + " · 다름 " + different + " · 없는 걸 채움 " + fabricated + " · 있는 걸 놓침 " + missed
                + " · 정밀도 " + percent(match, match + different + fabricated)
                + " · 재현율 " + percent(match, match + different + missed);
    }

    static String markdown(String modelName, String promptVersion, String set,
                           EvalScore score, LlmReuse reuse, Duration took) {
        StringBuilder md = new StringBuilder();
        md.append("# LLM 추출 정확도 — ").append(modelName).append(" · 프롬프트 ").append(promptVersion)
                .append(" · 표본 ").append(set).append("\n\n");
        md.append("- ").append(summary(score)).append('\n');
        md.append("- 모델 호출 ").append(reuse.calls()).append("번 · 재사용 ").append(reuse.reused()).append("번 · 걸린 시간 ")
                .append(took.toMinutes()).append("분 ").append(took.toSecondsPart()).append("초\n");
        md.append("- 근거 적중 ").append(score.evidenceHit()).append(" / ").append(score.evidenceChecked())
                .append(" (").append(percent(score.evidenceHit(), score.evidenceChecked())).append(")")
                .append(" — 값이 맞은 칸 중 정답 조각과 하나라도 겹친 칸\n");
        md.append("- 근거 검사에서 버린 것 — 근거 없는 값 ").append(score.droppedValues())
                .append(" · 무시한 근거 ").append(score.ignoredCitations())
                .append(" · 범위 밖 번호 ").append(score.droppedNumbers()).append("\n\n");

        md.append("## 칸별\n\n");
        md.append("| 칸 | 맞음 | 다름 | 없는 걸 채움 | 있는 걸 놓침 | 둘 다 빔 | 정밀도 | 재현율 |\n");
        md.append("|---|---|---|---|---|---|---|---|\n");
        for (String field : FieldNames.ALL) {
            int match = score.count(field, Verdict.MATCH);
            int different = score.count(field, Verdict.DIFFERENT);
            int fabricated = score.count(field, Verdict.FABRICATED);
            int missed = score.count(field, Verdict.MISSED);
            md.append("| ").append(field).append(" | ").append(match).append(" | ").append(different)
                    .append(" | ").append(fabricated).append(" | ").append(missed)
                    .append(" | ").append(score.count(field, Verdict.BOTH_EMPTY))
                    .append(" | ").append(percent(match, match + different + fabricated))
                    .append(" | ").append(percent(match, match + different + missed)).append(" |\n");
        }

        md.append("\n## 문서 탓 실패\n\n");
        if (score.failures().isEmpty()) {
            md.append("없음\n");
        } else {
            md.append("| 표본 | 까닭 | 메시지 |\n|---|---|---|\n");
            for (EvalScore.Failure failure : score.failures()) {
                md.append("| ").append(failure.sample().no()).append(" | ").append(failure.reason())
                        .append(" | ").append(cell(failure.message())).append(" |\n");
            }
        }

        md.append("\n## 갈린 칸\n\n");
        Map<Integer, List<Disagreement>> bySample = score.disagreements().stream()
                .collect(Collectors.groupingBy(d -> d.sample().no(), LinkedHashMap::new, Collectors.toList()));
        Map<Integer, SampleResult> results = score.results().stream()
                .collect(Collectors.toMap(r -> r.sample().no(), r -> r, (a, b) -> a, LinkedHashMap::new));
        int index = 0;
        for (Map.Entry<Integer, List<Disagreement>> entry : bySample.entrySet()) {
            SampleResult result = results.get(entry.getKey());
            md.append("### ").append(entry.getKey()).append(" · ").append(result.sample().source())
                    .append(" · ").append(result.sample().type()).append("\n\n");
            md.append("```\n").append(LlmPrompt.userMessage(result.segments())).append("\n```\n\n");
            md.append("| 번호 | 칸 | 경우 | 정답 | 모델 | 정답 조각 | 모델 조각 |\n|---|---|---|---|---|---|---|\n");
            for (Disagreement disagreement : entry.getValue()) {
                index++;
                md.append("| ").append(index).append(" | ").append(disagreement.field())
                        .append(" | ").append(label(disagreement.verdict()))
                        .append(" | ").append(cell(show(disagreement.gold())))
                        .append(" | ").append(cell(show(disagreement.model())))
                        .append(" | ").append(disagreement.goldSegments())
                        .append(" | ").append(disagreement.modelSegments()).append(" |\n");
            }
            md.append('\n');
        }
        if (index == 0) {
            md.append("없음\n");
        }

        md.append("\n## 무작위 ").append(RANDOM_SAMPLES).append("문서 — 정답과 모델이 같았던 칸\n\n");
        for (SampleResult result : randomSamples(score)) {
            md.append("### ").append(result.sample().no()).append(" · ").append(result.sample().source())
                    .append(" · ").append(result.sample().type()).append("\n\n");
            md.append("```\n").append(LlmPrompt.userMessage(result.segments())).append("\n```\n\n");
            md.append("| 칸 | 값 | 모델 조각 |\n|---|---|---|\n");
            int shown = 0;
            for (String field : FieldNames.ALL) {
                Object model = result.reading().fields().get(field);
                if (EvalScorer.judge(result.sample().fields().get(field), model) == Verdict.MATCH) {
                    shown++;
                    md.append("| ").append(field).append(" | ").append(cell(show(model)))
                            .append(" | ").append(result.modelSegments().getOrDefault(field, List.of())).append(" |\n");
                }
            }
            if (shown == 0) {
                md.append(bothEmpty(result)
                        ? "| (정답과 모델 모두 모든 칸을 비움) | | |\n"
                        : "| (같았던 값이 없음 — 이 표본의 칸은 위 「갈린 칸」 에 있음) | | |\n");
            }
            md.append('\n');
        }
        return md.toString();
    }

    // 같았던 값이 하나도 없을 때 그 까닭이 "둘 다 비움" 인지 "전부 갈림" 인지 가름
    // 둘을 한 문구로 쓰면 전부 갈린 표본을 둘 다 비운 것으로 잘못 보여 줌
    private static boolean bothEmpty(SampleResult result) {
        return FieldNames.ALL.stream().allMatch(field -> EvalScorer.judge(
                result.sample().fields().get(field), result.reading().fields().get(field)) == Verdict.BOTH_EMPTY);
    }

    // 채점이 끝난 표본 가운데 시드를 고정해 뽑음 — 다시 돌려도 같은 표본이 뽑힘
    static List<SampleResult> randomSamples(EvalScore score) {
        List<SampleResult> scored = new ArrayList<>(score.results().stream().filter(r -> r.failure() == null).toList());
        Collections.shuffle(scored, new Random(RANDOM_SEED));
        List<SampleResult> picked = new ArrayList<>(scored.subList(0, Math.min(RANDOM_SAMPLES, scored.size())));
        picked.sort((a, b) -> Integer.compare(a.sample().no(), b.sample().no()));
        return picked;
    }

    private static String json(String modelName, String promptVersion, String set, EvalScore score) {
        List<Map<String, Object>> samples = new ArrayList<>();
        for (SampleResult result : score.results()) {
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("no", result.sample().no());
            sample.put("id", result.sample().id());
            if (result.failure() != null) {
                sample.put("failure", result.failure().reason().name());
            } else {
                Map<String, Object> fields = new LinkedHashMap<>();
                for (String field : FieldNames.ALL) {
                    Object value = result.reading().fields().get(field);
                    fields.put(field, value instanceof Enum<?> e ? e.name() : value);
                }
                sample.put("fields", fields);
                sample.put("evidence", result.modelSegments());
            }
            samples.add(sample);
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("model", modelName);
        report.put("promptVersion", promptVersion);
        report.put("set", set);
        report.put("samples", samples);
        return MAPPER.writeValueAsString(report);
    }

    private static String percent(int part, int whole) {
        return whole == 0 ? "-" : String.format("%.1f%%", part * 100.0 / whole);
    }

    private static String label(Verdict verdict) {
        return switch (verdict) {
            case MATCH -> "맞음";
            case DIFFERENT -> "다름";
            case FABRICATED -> "없는 걸 채움";
            case MISSED -> "있는 걸 놓침";
            case BOTH_EMPTY -> "둘 다 빔";
        };
    }

    // 정답의 표기 목록은 "a | b" 로 보여 줌
    private static String show(Object value) {
        if (value == null) {
            return "—";
        }
        if (value instanceof List<?> items) {
            return items.stream()
                    .map(item -> item instanceof List<?> alternatives
                            ? alternatives.stream().map(Object::toString).collect(Collectors.joining(" | "))
                            : item.toString())
                    .collect(Collectors.joining(", ", "[", "]"));
        }
        return value instanceof Enum<?> e ? e.name() : value.toString();
    }

    // 표 칸 안의 세로선 · 줄바꿈이 표를 깨지 않게 바꿈
    private static String cell(String text) {
        return text == null ? "" : text.replace("|", "\\|").replace("\n", " ");
    }
}
