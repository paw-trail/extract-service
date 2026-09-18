package com.pawtrail.extract.application.service;

import com.pawtrail.extract.application.support.DocumentOutcome;
import com.pawtrail.extract.application.support.LlmReuse;
import com.pawtrail.extract.application.support.RunSummary;
import com.pawtrail.extract.application.support.RunSummary.Stop;
import com.pawtrail.extract.domain.enums.ExtractionMethod;
import com.pawtrail.extract.domain.enums.SourceType;
import com.pawtrail.extract.domain.exception.InternalCallException;
import com.pawtrail.extract.domain.exception.LlmUnavailableException;
import com.pawtrail.extract.domain.model.BulkResult;
import com.pawtrail.extract.domain.model.ConditionFields;
import com.pawtrail.extract.domain.model.IntraConflict;
import com.pawtrail.extract.domain.model.LlmAnswer;
import com.pawtrail.extract.domain.model.PendingDocument;
import com.pawtrail.extract.domain.model.PendingDocuments;
import com.pawtrail.extract.domain.model.PolicyItem;
import com.pawtrail.extract.domain.model.Segment;
import com.pawtrail.extract.domain.model.StatusMark;
import com.pawtrail.extract.domain.model.StatusResult;
import com.pawtrail.extract.domain.provider.LlmProvider;
import com.pawtrail.extract.domain.provider.PolicyProvider;
import com.pawtrail.extract.domain.provider.RawDocumentProvider;
import com.pawtrail.extract.infrastructure.config.ExtractProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 청크 루프와 멈춤 규칙을 봅니다.
 *
 * 원문 한 건의 처리는 원문 식별자(sourceId)의 머리말로 정해 둔 흉내입니다.
 * <pre>
 * send-   보낼 항목   ·   conf-  충돌 하나 달린 항목   ·   skip-  보내지 않고 완료
 * fail-   문서 탓 실패  ·   down-  모델을 부를 수 없음
 * </pre>
 * ingest 흉내는 되돌려 쓴 원문을 대기에서 빼므로 진짜처럼 언제나 첫 쪽을 줍니다.
 */
class ExtractRunnerTest {

    private static final LocalDateTime STARTED = LocalDateTime.of(2026, 9, 18, 12, 0);

    private final FakeIngest ingest = new FakeIngest();
    private final FakePolicy policy = new FakePolicy();

    @Test
    @DisplayName("대기가 빌 때까지 청크를 돌고 보냄 · 완료 · 충돌을 센다")
    void 끝까지_돔() {
        ingest.add("send-1", "conf-2", "skip-3", "send-4", "send-5");

        RunSummary summary = runner(2, 5).run(STARTED, null);

        assertThat(summary.stop()).isEqualTo(Stop.DRAINED);
        assertThat(summary.chunks()).isEqualTo(3);
        assertThat(summary.fetched()).isEqualTo(5);
        assertThat(summary.sent()).isEqualTo(4);
        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.conflicts()).isEqualTo(1);
        assertThat(ingest.pending).isEmpty();
        assertThat(policy.batches).hasSize(3);
        assertThat(policy.extractedAt).containsOnly(STARTED);
    }

    @Test
    @DisplayName("보낼 항목이 없는 청크는 bulk 를 부르지 않고 상태만 되돌려 쓴다")
    void 보낼_것_없는_청크() {
        ingest.add("skip-1", "skip-2");

        RunSummary summary = runner(10, 5).run(STARTED, null);

        assertThat(policy.batches).isEmpty();
        assertThat(ingest.done).hasSize(2);
        assertThat(summary.skipped()).isEqualTo(2);
    }

    @Test
    @DisplayName("상한만큼만 처리한다 — 청크 크기를 상한에 맞춰 줄인다")
    void 상한() {
        ingest.add("send-1", "send-2", "send-3", "send-4");

        RunSummary summary = runner(2, 5).run(STARTED, 3);

        assertThat(summary.stop()).isEqualTo(Stop.LIMIT);
        assertThat(summary.fetched()).isEqualTo(3);
        assertThat(ingest.requestedSizes).containsExactly(2, 1);
        assertThat(ingest.pending.values()).extracting(PendingDocument::sourceId).containsExactly("send-4");
    }

    @Test
    @DisplayName("문서 탓 실패가 연달아 나면 처리한 데까지 보내고 되돌려 쓴 뒤 멈춘다")
    void 연달아_실패() {
        ingest.add("send-1", "fail-2", "fail-3", "fail-4", "send-5");

        RunSummary summary = runner(10, 3).run(STARTED, null);

        assertThat(summary.stop()).isEqualTo(Stop.TOO_MANY_FAILURES);
        // 앞의 한 건은 보내고, 실패 셋은 처리 실패로 되돌려 써 대기열 앞을 막지 않음
        assertThat(policy.batches).hasSize(1);
        assertThat(policy.batches.getFirst()).hasSize(1);
        assertThat(ingest.failed).hasSize(3);
        // 보지 않은 원문은 대기 그대로
        assertThat(ingest.pending.values()).extracting(PendingDocument::sourceId).containsExactly("send-5");
    }

    @Test
    @DisplayName("흩어진 실패는 멈추지 않는다 — 성공이 사이에 끼면 다시 센다")
    void 흩어진_실패() {
        ingest.add("fail-1", "fail-2", "send-3", "fail-4", "fail-5");

        RunSummary summary = runner(10, 3).run(STARTED, null);

        assertThat(summary.stop()).isEqualTo(Stop.DRAINED);
        assertThat(summary.failed()).isEqualTo(4);
    }

    @Test
    @DisplayName("모델을 부를 수 없으면 그 청크는 보내지도 되돌려 쓰지도 않고 멈춘다")
    void 모델_불가() {
        ingest.add("send-1", "send-2", "send-3", "down-4");

        RunSummary summary = runner(2, 5).run(STARTED, null);

        assertThat(summary.stop()).isEqualTo(Stop.LLM_UNAVAILABLE);
        // 첫 청크는 끝났고, 둘째 청크는 통째로 대기에 남음
        assertThat(ingest.done).hasSize(2);
        assertThat(ingest.pending.values()).extracting(PendingDocument::sourceId).containsExactly("send-3", "down-4");
        assertThat(policy.batches).hasSize(1);
    }

    @Test
    @DisplayName("policy 가 받지 않으면 그 청크 상태를 바꾸지 않고 멈춘다")
    void 보내기_실패() {
        ingest.add("send-1", "send-2");
        policy.fail = true;

        RunSummary summary = runner(10, 5).run(STARTED, null);

        assertThat(summary.stop()).isEqualTo(Stop.INTERNAL_CALL);
        assertThat(ingest.done).isEmpty();
        assertThat(ingest.pending).hasSize(2);
    }

    @Test
    @DisplayName("같은 장소 · 같은 소스를 한 실행에서 두 번 보내면 센다")
    void 형제_원문() {
        UUID place = UUID.randomUUID();
        ingest.add(new PendingDocument(UUID.randomUUID(), SourceType.PET_TOUR, "send-a", place, Map.of(), "h"));
        ingest.add(new PendingDocument(UUID.randomUUID(), SourceType.PET_TOUR, "send-b", place, Map.of(), "h"));
        ingest.add(new PendingDocument(UUID.randomUUID(), SourceType.GOCAMPING, "send-c", place, Map.of(), "h"));

        RunSummary summary = runner(10, 5).run(STARTED, null);

        assertThat(summary.siblings()).isEqualTo(1);
    }

    @Test
    @DisplayName("두 청크 연달아 상태가 하나도 안 바뀌면 멈춘다 — 같은 첫 쪽을 끝없이 돌지 않게")
    void 진척_없음() {
        ingest.add("send-1", "send-2");
        ingest.stuck = true;

        RunSummary summary = runner(10, 5).run(STARTED, null);

        assertThat(summary.stop()).isEqualTo(Stop.NO_PROGRESS);
        assertThat(summary.chunks()).isEqualTo(2);
    }

    @Test
    @DisplayName("모델 이름이 policy 가 받는 길이를 넘으면 시작하지 않는다")
    void 설정_길이() {
        ingest.add("send-1");

        RunSummary summary = runner(10, 5, "m".repeat(51)).run(STARTED, null);

        assertThat(summary.stop()).isEqualTo(Stop.BAD_SETTINGS);
        assertThat(ingest.requestedSizes).isEmpty();
    }

    private ExtractRunner runner(int chunkSize, int maxFailures) {
        return runner(chunkSize, maxFailures, "gpt-5.6-luna");
    }

    private ExtractRunner runner(int chunkSize, int maxFailures, String model) {
        LlmExtractor llmExtractor = new LlmExtractor(new NamedLlm(model));
        ExtractProperties properties = new ExtractProperties(chunkSize, maxFailures, new ExtractProperties.Policy(60));
        return new ExtractRunner(ingest, policy, new ScriptedExtraction(llmExtractor), llmExtractor, properties);
    }

    // 원문 식별자의 머리말로 결과를 정하는 한 건 처리
    private static final class ScriptedExtraction extends DocumentExtraction {

        ScriptedExtraction(LlmExtractor llmExtractor) {
            super(List.of(), llmExtractor);
        }

        @Override
        public DocumentOutcome extract(PendingDocument document, LlmReuse reuse) {
            String id = document.sourceId();
            if (id.startsWith("down-")) {
                throw new LlmUnavailableException("연결 실패", new RuntimeException());
            }
            if (id.startsWith("fail-")) {
                return DocumentOutcome.failed("흉내 실패");
            }
            if (id.startsWith("skip-")) {
                return DocumentOutcome.skipped("흉내 동물병원");
            }
            List<IntraConflict> conflicts = id.startsWith("conf-")
                    ? List.of(new IntraConflict("scope", "불가능", "소형견만"))
                    : List.of();
            return DocumentOutcome.send(new PolicyItem(document.placeId(), document.source(),
                    ConditionFields.empty(), List.of(), conflicts, ExtractionMethod.RULE));
        }
    }

    // 되돌려 쓴 원문을 대기에서 빼는 ingest 흉내
    private static final class FakeIngest implements RawDocumentProvider {

        final Map<UUID, PendingDocument> pending = new LinkedHashMap<>();
        final List<StatusMark> done = new ArrayList<>();
        final List<StatusMark> failed = new ArrayList<>();
        final List<Integer> requestedSizes = new ArrayList<>();
        boolean stuck;

        void add(String... sourceIds) {
            for (String sourceId : sourceIds) {
                add(new PendingDocument(UUID.randomUUID(), SourceType.PET_TOUR, sourceId,
                        UUID.randomUUID(), Map.of(), "hash-" + sourceId));
            }
        }

        void add(PendingDocument document) {
            pending.put(document.id(), document);
        }

        @Override
        public PendingDocuments findPending(int size) {
            requestedSizes.add(size);
            return new PendingDocuments(pending.size(), pending.values().stream().limit(size).toList());
        }

        @Override
        public StatusResult markStatus(List<StatusMark> done, List<StatusMark> failed) {
            if (stuck) {
                return new StatusResult(0, done.size() + failed.size());
            }
            this.done.addAll(done);
            this.failed.addAll(failed);
            done.forEach(mark -> pending.remove(mark.id()));
            failed.forEach(mark -> pending.remove(mark.id()));
            return new StatusResult(done.size() + failed.size(), 0);
        }
    }

    private static final class FakePolicy implements PolicyProvider {

        final List<List<PolicyItem>> batches = new ArrayList<>();
        final List<LocalDateTime> extractedAt = new ArrayList<>();
        boolean fail;

        @Override
        public BulkResult bulk(String extractedBy, String promptVersion, LocalDateTime at, List<PolicyItem> items) {
            if (fail) {
                throw new InternalCallException("policy 흉내 실패");
            }
            batches.add(List.copyOf(items));
            extractedAt.add(at);
            return new BulkResult(items.size(), items.size());
        }
    }

    // 이름만 쓰는 모델 흉내 — 한 건 처리를 흉내로 바꿔 부르지 않음
    private record NamedLlm(String name) implements LlmProvider {

        @Override
        public LlmAnswer read(List<Segment> segments) {
            throw new AssertionError("부르지 않아야 함");
        }

        @Override
        public String modelName() {
            return name;
        }

        @Override
        public String promptVersion() {
            return "v2";
        }
    }
}
