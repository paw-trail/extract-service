package com.pawtrail.extract.application.service;

import com.pawtrail.extract.application.support.DocumentOutcome;
import com.pawtrail.extract.application.support.LlmReuse;
import com.pawtrail.extract.application.support.RunSummary;
import com.pawtrail.extract.application.support.RunSummary.Stop;
import com.pawtrail.extract.domain.exception.InternalCallException;
import com.pawtrail.extract.domain.exception.LlmUnavailableException;
import com.pawtrail.extract.domain.model.MergedReading;
import com.pawtrail.extract.domain.model.PendingDocument;
import com.pawtrail.extract.domain.model.PendingDocuments;
import com.pawtrail.extract.domain.model.PolicyItem;
import com.pawtrail.extract.domain.model.StatusMark;
import com.pawtrail.extract.domain.model.StatusResult;
import com.pawtrail.extract.domain.provider.PolicyProvider;
import com.pawtrail.extract.domain.provider.RawDocumentProvider;
import com.pawtrail.extract.domain.rule.ConditionNormalizer;
import com.pawtrail.extract.domain.rule.PolicyItemCheck;
import com.pawtrail.extract.domain.rule.ReadingMerger;
import com.pawtrail.extract.infrastructure.config.ExtractProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ingest 의 대기 원문을 한 청크씩 끝까지 처리합니다.
 *
 * <pre>
 * ① 장소에 이어진 대기 원문을 청크 크기만큼 가져옴 — 언제나 첫 쪽
 * ② 원문마다 항목을 만듦 (DocumentExtraction)
 * ③ 보낼 항목을 policy bulk 로 한 번에
 * ④ 처리 완료 · 실패를 내용 해시와 함께 ingest 에 되돌려 씀
 * ⑤ 대기가 비거나 요청한 건수에 닿을 때까지 되풀이
 * </pre>
 *
 * <b>청크는 순차로 하나씩, 모델도 한 번에 하나씩 부릅니다.</b>
 * 전량이 1시간 안팎이라 충분하고, 첫 실행의 로그가 순서대로 읽힙니다.
 *
 * <b>멈추는 자리마다 그 청크를 어떻게 두는지가 다릅니다.</b>
 * <pre>
 * 문서 탓 실패가 연달아 남      처리한 데까지 보내고 되돌려 씀 — 실패 원문이 대기열 앞을 막지 않게
 * 모델을 부를 수 없음          그 청크 상태를 안 바꿈 — 원문 탓이 아니라 다음 실행이 다시 가져가야 함
 * ingest · policy 호출 실패   그 청크 상태를 안 바꿈 — 보낸 것이 다시 뽑혀 한 번 더 보내질 뿐임
 * 두 청크 연달아 안 바뀜       멈춤 — 되돌려 쓰기가 먹지 않으면 같은 첫 쪽을 끝없이 돎
 * </pre>
 *
 * <b>같은 장소 · 같은 소스의 다른 원문(형제 원문)은 앞의 결과와 안전 쪽으로 합쳐 다시 보냅니다.</b>
 * policy 는 장소 · 소스마다 한 행이라 그냥 보내면 나중 것이 앞의 것을 덮는데, 나중은 적재 순서라
 * 이기는 쪽이 사실상 임의입니다. 모델 두 읽기와 같은 규칙(ReadingMerger)으로 합쳐 좁은 쪽이 남게 합니다.
 * 한 실행 안에서만 모읍니다 — 증분 실행에서 한쪽만 다시 들어오면 합칠 짝이 없어 그 원문만으로 덮입니다.
 *
 * 실행 기록 표를 두지 않습니다. 어디까지 했는지는 ingest 원문 상태가 맡고,
 * 끝날 때 요약 로그 한 줄을 남깁니다.
 */
@Service
public class ExtractRunner {

    private static final Logger log = LoggerFactory.getLogger(ExtractRunner.class);

    private final RawDocumentProvider rawDocuments;
    private final PolicyProvider policies;
    private final DocumentExtraction extraction;
    private final LlmExtractor llmExtractor;
    private final ExtractProperties properties;

    public ExtractRunner(RawDocumentProvider rawDocuments, PolicyProvider policies,
                         DocumentExtraction extraction, LlmExtractor llmExtractor,
                         ExtractProperties properties) {
        this.rawDocuments = rawDocuments;
        this.policies = policies;
        this.extraction = extraction;
        this.llmExtractor = llmExtractor;
        this.properties = properties;
    }

    /**
     * @param startedAt 실행을 시작한 시각 — 이 실행이 보낸 모든 항목의 추출 시각
     * @param limit     처리할 원문 수의 상한 — null 이면 대기가 빌 때까지
     */
    public RunSummary run(LocalDateTime startedAt, Integer limit) {
        String extractedBy = llmExtractor.modelName();
        String promptVersion = llmExtractor.promptVersion();
        Tally tally = new Tally();
        LlmReuse reuse = new LlmReuse();
        long began = System.nanoTime();

        List<String> settings = PolicyItemCheck.checkBatch(extractedBy, promptVersion);
        if (!settings.isEmpty()) {
            log.error("모델 이름 · 프롬프트 판이 policy 가 받는 길이를 넘어 시작하지 않습니다: {}", settings);
            return finish(Stop.BAD_SETTINGS, tally, reuse, began, startedAt);
        }
        log.info("추출 실행을 시작합니다. 모델={} 판={} 상한={} 청크={}",
                extractedBy, promptVersion, limit == null ? "없음" : limit, properties.chunkSize());

        Stop stop;
        try {
            stop = loop(startedAt, limit, extractedBy, promptVersion, tally, reuse);
        } catch (LlmUnavailableException e) {
            log.error("모델을 부를 수 없어 멈춥니다. 지금 청크의 원문 상태는 그대로 둡니다.", e);
            stop = Stop.LLM_UNAVAILABLE;
        } catch (InternalCallException e) {
            log.error("ingest · policy 를 부르지 못해 멈춥니다. 지금 청크의 원문 상태는 그대로 둡니다.", e);
            stop = Stop.INTERNAL_CALL;
        }
        return finish(stop, tally, reuse, began, startedAt);
    }

    private Stop loop(LocalDateTime startedAt, Integer limit, String extractedBy, String promptVersion,
                      Tally tally, LlmReuse reuse) {
        int consecutiveFailures = 0;
        int idleChunks = 0;
        // 장소 · 소스마다 이 실행에서 보낸 결과 — 다른 원문이 같은 자리로 오면 형제 원문이라 합침
        // 같은 원문이 다시 오는 것(가져간 사이 재수집으로 대기에 남았던 것)은 형제가 아니라 새 내용으로 바꿈
        Map<String, Sent> sent = new HashMap<>();

        while (true) {
            int size = limit == null
                    ? properties.chunkSize()
                    : Math.min(properties.chunkSize(), limit - tally.fetched);
            if (size <= 0) {
                return Stop.LIMIT;
            }

            PendingDocuments page = rawDocuments.findPending(size);
            if (page.documents().isEmpty()) {
                return Stop.DRAINED;
            }

            // 이 청크의 수 — 보내고 되돌려 쓰기까지 끝난 뒤에만 실행 합계에 더함
            // 중간에 멈춘 청크는 원문 상태가 그대로라 합계에 넣으면 요약이 실제와 어긋남
            List<PolicyItem> items = new ArrayList<>();
            // 이 청크 안에서 장소 · 소스마다 몇 번째 항목인지 — 형제를 합치면 그 자리를 바꿔 끼움
            Map<String, Integer> itemIndex = new HashMap<>();
            List<StatusMark> done = new ArrayList<>();
            List<StatusMark> failed = new ArrayList<>();
            int skipped = 0;
            int siblings = 0;
            boolean tooManyFailures = false;

            for (PendingDocument document : page.documents()) {
                DocumentOutcome outcome = extraction.extract(document, reuse);
                switch (outcome.kind()) {
                    case SEND -> {
                        String key = document.placeId() + "/" + document.source();
                        PolicyItem item = outcome.item();
                        Sent earlier = sent.get(key);
                        if (earlier != null && !earlier.documentId().equals(document.id())) {
                            MergedReading merged = ConditionNormalizer.normalize(
                                    ReadingMerger.merge(earlier.reading(), readingOf(item)));
                            item = PolicyItem.of(document.placeId(), document.source(), merged);
                            siblings++;
                            log.info("같은 장소 · 같은 소스의 원문을 앞의 원문과 안전 쪽으로 합쳐 다시 보냅니다. "
                                    + "장소={} 소스={} 원문={}", document.placeId(), document.source(), document.sourceId());
                        }
                        sent.put(key, new Sent(document.id(), readingOf(item)));
                        Integer index = itemIndex.get(key);
                        if (index == null) {
                            itemIndex.put(key, items.size());
                            items.add(item);
                        } else {
                            items.set(index, item);
                        }
                        done.add(document.mark());
                        consecutiveFailures = 0;
                    }
                    case SKIPPED -> {
                        done.add(document.mark());
                        skipped++;
                        consecutiveFailures = 0;
                    }
                    case FAILED -> {
                        failed.add(document.mark());
                        consecutiveFailures++;
                        log.warn("원문을 처리 실패로 둡니다. 소스={} 원문={} 까닭={}",
                                document.source(), document.sourceId(), outcome.reason());
                    }
                }
                if (consecutiveFailures >= properties.maxConsecutiveFailures()) {
                    tooManyFailures = true;
                    break;
                }
            }

            int conflicts = items.stream().mapToInt(item -> item.conflicts().size()).sum();
            if (!items.isEmpty()) {
                policies.bulk(extractedBy, promptVersion, startedAt, items);
            }
            StatusResult status = rawDocuments.markStatus(done, failed);

            tally.chunks++;
            tally.fetched += done.size() + failed.size();
            tally.sent += items.size();
            tally.skipped += skipped;
            tally.failed += failed.size();
            tally.conflicts += conflicts;
            tally.siblings += siblings;
            tally.statusSkipped += status.skipped();
            log.info("청크 {} — 보냄 {} · 실패 {} · 충돌 {} · 상태 바꿈 {} · 건너뜀 {} · 누적 {} · 남은 대기 {}",
                    tally.chunks, items.size(), failed.size(), conflicts, status.updated(), status.skipped(),
                    tally.fetched, Math.max(page.total() - status.updated(), 0));

            if (tooManyFailures) {
                log.error("문서 탓 실패가 {}번 연달아 나 멈춥니다. 처리한 데까지는 보내고 되돌려 썼습니다.",
                        properties.maxConsecutiveFailures());
                return Stop.TOO_MANY_FAILURES;
            }

            idleChunks = status.updated() == 0 ? idleChunks + 1 : 0;
            if (idleChunks >= 2) {
                log.error("두 청크 연달아 원문 상태가 하나도 안 바뀌어 멈춥니다. 같은 원문을 끝없이 돌 수 있습니다.");
                return Stop.NO_PROGRESS;
            }
        }
    }

    private RunSummary finish(Stop stop, Tally tally, LlmReuse reuse, long began, LocalDateTime startedAt) {
        RunSummary summary = new RunSummary(stop, tally.chunks, tally.fetched, tally.sent, tally.skipped,
                tally.failed, tally.conflicts, tally.siblings, tally.statusSkipped,
                reuse.calls(), reuse.reused(), Duration.ofNanos(System.nanoTime() - began));
        log.info("추출 실행 끝 — 멈춘 까닭 {} · 시작 {} · 청크 {} · 가져옴 {} · 보냄 {} · 보내지 않음 {} · 실패 {} "
                        + "· 소스 내 충돌 {} · 형제 원문 {} · 상태 건너뜀 {} · 모델 호출 {} · 재사용 {} · 걸린 시간 {}",
                summary.stop(), startedAt, summary.chunks(), summary.fetched(), summary.sent(), summary.skipped(),
                summary.failed(), summary.conflicts(), summary.siblings(), summary.statusSkipped(),
                summary.calls(), summary.reused(), format(summary.elapsed()));
        return summary;
    }

    private static String format(Duration elapsed) {
        long seconds = elapsed.toSeconds();
        return "%d시간 %d분 %d초".formatted(seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    private static MergedReading readingOf(PolicyItem item) {
        return new MergedReading(item.fields(), item.evidence(), item.conflicts(), item.method());
    }

    // 이 실행에서 한 장소 · 소스로 보낸 결과와 그것을 낸 원문 — 형제 원문이 오면 이것과 합침
    private record Sent(UUID documentId, MergedReading reading) {
    }

    // 실행 한 번 동안 늘어나는 수 — 끝의 요약이 됨
    private static final class Tally {
        int chunks;
        int fetched;
        int sent;
        int skipped;
        int failed;
        int conflicts;
        int siblings;
        int statusSkipped;
    }
}
