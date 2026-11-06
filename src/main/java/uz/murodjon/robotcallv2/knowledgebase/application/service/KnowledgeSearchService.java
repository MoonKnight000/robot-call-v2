package uz.murodjon.robotcallv2.knowledgebase.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallEventUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallEvent;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallEventType;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.*;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeRetrievalUseCase;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeSearchUseCase;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItemMatch;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgePassage;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeQuery;
import uz.murodjon.robotcallv2.knowledgebase.domain.service.KnowledgeItemMatcher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * The playground and the insights page. The playground runs the very searches a call runs, with the agent's own
 * top-k and similarity floor, so what it shows is what a caller would have got. The insights are read from the
 * calls' traces ({@link CallEventType#KNOWLEDGE_RETRIEVED}) — nothing is counted twice and nothing extra is stored.
 */
@Service
public class KnowledgeSearchService implements KnowledgeSearchUseCase {

    /** What a call hands the model when neither the agent nor the stage says (dialog.knowledge-passages). */
    private static final int DEFAULT_LIMIT = 3;
    /** The newest searches read for one insights page — months of a busy line; beyond it the page says truncated. */
    private static final int MAX_EVENTS = 5000;
    private static final int MAX_DAYS = 90;
    private static final int MAX_GAPS = 50;
    private static final int MAX_TOP = 20;

    private final KnowledgeRetrievalUseCase knowledgeRetrievalUseCase;
    private final AiAgentUseCase aiAgentUseCase;
    private final CallEventUseCase callEventUseCase;
    private final Clock clock;

    public KnowledgeSearchService(KnowledgeRetrievalUseCase knowledgeRetrievalUseCase, AiAgentUseCase aiAgentUseCase,
                                  CallEventUseCase callEventUseCase, Clock clock) {
        this.knowledgeRetrievalUseCase = knowledgeRetrievalUseCase;
        this.aiAgentUseCase = aiAgentUseCase;
        this.callEventUseCase = callEventUseCase;
        this.clock = clock;
    }

    @Override
    public KnowledgeSearchResponse searchKnowledge(long companyId, KnowledgeSearchRequest request) {
        AiAgent agent = request.agentId() != null ? aiAgentUseCase.requireAgent(companyId, request.agentId()) : null;
        int limit = request.limit() != null ? request.limit()
                : agent != null && agent.ragTopK() != null ? agent.ragTopK() : DEFAULT_LIMIT;
        double minScore = request.minScore() != null ? request.minScore()
                : agent != null && agent.ragMinScore() != null ? agent.ragMinScore()
                : KnowledgeRetrievalService.MIN_SEMANTIC_SCORE;
        String question = request.question().trim();

        long startedAt = System.currentTimeMillis();
        KnowledgeItemMatch curated = knowledgeRetrievalUseCase.findCuratedAnswer(companyId, request.agentId(), question,
                request.language());
        List<KnowledgePassage> passages = knowledgeRetrievalUseCase.searchPassages(companyId, request.agentId(),
                List.of(), new KnowledgeQuery(question, question, minScore), limit);
        return new KnowledgeSearchResponse(curated, passages, limit, minScore, System.currentTimeMillis() - startedAt);
    }

    @Override
    public KnowledgeInsightsResponse findInsights(long companyId, int days, Long agentId) {
        if (agentId != null) {
            aiAgentUseCase.requireAgent(companyId, agentId);
        }
        Instant to = clock.instant();
        Instant from = to.minus(Duration.ofDays(Math.clamp(days, 1, MAX_DAYS)));
        List<CallEvent> events = callEventUseCase.findByCompanyIdAndType(companyId, CallEventType.KNOWLEDGE_RETRIEVED,
                from, to, MAX_EVENTS);

        int searches = 0;
        int answered = 0;
        int curatedAnswers = 0;
        Set<String> seen = new HashSet<>();
        Map<String, Gap> gaps = new LinkedHashMap<>();
        Map<Long, SourceUsage> sources = new HashMap<>();
        Map<Long, int[]> items = new HashMap<>();
        // Newest first: the first time a gap is met is the latest time it was asked.
        for (CallEvent event : events) {
            Map<String, Object> payload = event.payload();
            if (agentId != null && !agentId.equals(asLong(payload.get("agentId")))) {
                continue;
            }
            String question = payload.get("question") instanceof String text ? text : "";
            String normalized = KnowledgeItemMatcher.normalize(question);
            // A turn can be searched twice (a speculative reply, then the final one); one call asking one thing is one.
            if (!seen.add(event.callAttemptId() + ":" + event.chatSessionId() + ":" + normalized)) {
                continue;
            }
            searches++;
            Long itemId = asLong(payload.get("curatedItemId"));
            if (itemId != null) {
                curatedAnswers++;
                int[] usage = items.computeIfAbsent(itemId, id -> new int[2]);
                usage[0]++;
                if ("MEANING".equals(payload.get("curatedMatchedBy"))) {
                    usage[1]++;
                }
            }
            if (Boolean.TRUE.equals(payload.get("found"))) {
                answered++;
                countSources(payload.get("passages"), sources);
            } else if (!normalized.isEmpty()) {
                gaps.computeIfAbsent(normalized, key -> new Gap(question, event)).count++;
            }
        }

        List<KnowledgeGapRow> gapRows = gaps.values().stream()
                .sorted(Comparator.comparingInt((Gap gap) -> gap.count).reversed()
                        .thenComparing(gap -> gap.latest.at(), Comparator.reverseOrder()))
                .limit(MAX_GAPS)
                .map(gap -> new KnowledgeGapRow(gap.question, gap.count, gap.latest.at(),
                        gap.latest.callAttemptId(), gap.latest.chatSessionId()))
                .toList();
        List<KnowledgeSourceUsageRow> sourceRows = sources.entrySet().stream()
                .map(entry -> new KnowledgeSourceUsageRow(entry.getKey(), entry.getValue().name, entry.getValue().hits))
                .sorted(Comparator.comparingInt(KnowledgeSourceUsageRow::hits).reversed())
                .limit(MAX_TOP)
                .toList();
        List<KnowledgeItemUsageRow> itemRows = items.entrySet().stream()
                .map(entry -> new KnowledgeItemUsageRow(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .sorted(Comparator.comparingInt(KnowledgeItemUsageRow::hits).reversed())
                .limit(MAX_TOP)
                .toList();
        double hitRate = searches == 0 ? 0.0 : Math.round(1000.0 * answered / searches) / 1000.0;
        return new KnowledgeInsightsResponse(from, to, searches, answered, curatedAnswers, hitRate, gapRows,
                sourceRows, itemRows, events.size() >= MAX_EVENTS);
    }

    /** Each source counted once per search, however many of its passages the search returned. */
    private static void countSources(Object passages, Map<Long, SourceUsage> sources) {
        if (!(passages instanceof List<?> list)) {
            return;
        }
        Set<Long> counted = new HashSet<>();
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> passage) {
                Long sourceId = asLong(passage.get("sourceId"));
                if (sourceId != null && counted.add(sourceId)) {
                    SourceUsage usage = sources.computeIfAbsent(sourceId,
                            id -> new SourceUsage(String.valueOf(passage.get("sourceName"))));
                    usage.hits++;
                }
            }
        }
    }

    private static Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    /** A question nothing was found for, with its latest asking. */
    private static final class Gap {
        private final String question;
        private final CallEvent latest;
        private int count;

        private Gap(String question, CallEvent latest) {
            this.question = question;
            this.latest = latest;
        }
    }

    private static final class SourceUsage {
        private final String name;
        private int hits;

        private SourceUsage(String name) {
            this.name = name;
        }
    }
}
