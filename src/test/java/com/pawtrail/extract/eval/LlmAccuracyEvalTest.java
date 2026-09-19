package com.pawtrail.extract.eval;

import com.pawtrail.extract.application.service.LlmExtractor;
import com.pawtrail.extract.application.support.LlmReuse;
import com.pawtrail.extract.domain.enums.ExtractionMethod;
import com.pawtrail.extract.domain.enums.SourceType;
import com.pawtrail.extract.domain.exception.LlmDocumentException;
import com.pawtrail.extract.domain.model.LlmReading;
import com.pawtrail.extract.domain.model.MergedReading;
import com.pawtrail.extract.domain.model.Segment;
import com.pawtrail.extract.domain.model.SourceText;
import com.pawtrail.extract.domain.provider.PayloadReader;
import com.pawtrail.extract.domain.rule.ConditionNormalizer;
import com.pawtrail.extract.domain.rule.ConditionRules;
import com.pawtrail.extract.domain.rule.Segmenter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정답 표본으로 LLM 추출의 칸별 정확도를 잽니다. 모델을 실제로 부릅니다.
 *
 * 기본 빌드에서는 돌지 않습니다 — build.gradle 의 test 작업이 llm-eval 태그를 빼고,
 * llmEval 작업만 이 태그를 돌립니다.
 * <pre>
 * ./gradlew llmEval                                          로컬 Ollama · 평가 표본 100건
 * ./gradlew llmEval "-Dapp.extract.llm.provider=openai"      OpenAI (OPENAI_API_KEY 환경변수)
 * ./gradlew llmEval "-Dllm.eval.set=holdout"                 새 표본 40건 — 프롬프트 판을 견줄 때
 * </pre>
 * -D 인자는 따옴표로 감쌉니다. PowerShell 은 감싸지 않으면 첫 점에서 잘라 작업 이름으로 넘깁니다.
 *
 * OpenAI 는 설정(openai.second-reasoning-effort)대로 두 번 읽어 합친 결과를 잽니다.
 * 보고서 이름에 medium+high 가 붙어 한 번 읽기 보고서를 덮지 않습니다.
 * 한 번 읽기와 견주려면 "-Dapp.extract.llm.openai.second-reasoning-effort=" 로 비웁니다.
 * 채점은 정규화를 거친 값으로 합니다 — policy 로 나가는 값과 같게.
 *
 * 앱의 실제 빈 배선을 탑니다 — 설정 키로 구현 하나가 뜨는 것 · 원문 읽기 · 규칙 · 조각 · 프롬프트 ·
 * 답 읽기 · 근거 검사가 운영과 같습니다. policy 에는 아무것도 쓰지 않습니다.
 *
 * 재는 작업이라 정확도가 낮아도 실패하지 않습니다. 모델을 부를 수 없으면(LlmUnavailableException)
 * 그 자리에서 실패합니다 — 반쪽 표본으로 낸 숫자가 보고서로 남지 않게 하려는 것입니다.
 */
@Tag("llm-eval")
@SpringBootTest
class LlmAccuracyEvalTest {

    @Autowired
    private LlmExtractor llmExtractor;

    @Autowired
    private List<PayloadReader> payloadReaders;

    // 표본 묶음 — build.gradle 의 llmEval 작업이 -Dllm.eval.set 값을 넘겨 줌
    private static final String SET_PROPERTY = "llm.eval.set";

    @Test
    @DisplayName("정답 표본으로 LLM 추출의 칸별 정확도를 잰다")
    void 정확도() {
        Map<SourceType, PayloadReader> readers = payloadReaders.stream()
                .collect(Collectors.toMap(PayloadReader::source, Function.identity()));
        String set = System.getProperty(SET_PROPERTY, EvalAnswers.DEFAULT_SET);
        List<EvalSample> samples = EvalAnswers.load(set);
        System.out.println("표본 " + set + " · " + samples.size() + "건 · " + llmExtractor.modelName()
                + " · 프롬프트 " + llmExtractor.promptVersion());
        LlmReuse reuse = new LlmReuse();
        EvalScore score = new EvalScore();
        long started = System.nanoTime();

        for (EvalSample sample : samples) {
            PayloadReader reader = readers.get(SourceType.valueOf(sample.source()));
            List<SourceText> texts = ConditionRules.extract(reader.read(EvalAnswers.payload(sample.id()))).texts();
            List<Segment> segments = Segmenter.split(texts);
            try {
                LlmReading reading = llmExtractor.extract(texts, reuse);
                score.add(sample, segments, normalized(reading));
            } catch (LlmDocumentException e) {
                score.addFailure(sample, segments, e);
            }
            System.out.printf("  %d/%d · %s · %s%n", sample.no(), samples.size(), sample.source(), sample.type());
        }

        Duration took = Duration.ofNanos(System.nanoTime() - started);
        Path report = EvalReportWriter.write(Path.of("build", "reports", "llm-eval"),
                llmExtractor.modelName(), llmExtractor.promptVersion(), set, score, reuse, took);
        System.out.println(EvalReportWriter.summary(score));
        System.out.println("보고서 " + report.toAbsolutePath());

        assertThat(score.results()).hasSize(samples.size());
    }

    /**
     * 정규화를 거친 값으로 채점합니다.
     *
     * policy 로 나가는 것이 정규화된 값이라 그 기준으로 재야 실제와 맞습니다.
     * 모델이 동반 불가를 범위로만 말하고 실내 · 실외를 비워 두는 것처럼, 뜻이 칸 사이에서 따라 나오는
     * 자리는 코드가 채우므로 모델 탓으로 세지 않습니다.
     */
    private static LlmReading normalized(LlmReading reading) {
        MergedReading normalized = ConditionNormalizer.normalize(
                new MergedReading(reading.fields(), reading.evidence(), List.of(), ExtractionMethod.LLM));
        return new LlmReading(normalized.fields(), normalized.evidence(),
                reading.droppedValues(), reading.ignoredCitations(), reading.droppedNumbers());
    }
}
