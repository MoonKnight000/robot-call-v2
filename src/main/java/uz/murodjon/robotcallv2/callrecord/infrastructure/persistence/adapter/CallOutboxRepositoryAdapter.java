package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.callrecord.application.dto.PendingNote;
import uz.murodjon.robotcallv2.callrecord.application.dto.PendingSummary;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallOutboxRepository;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallResultEntity;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallAttemptJpaRepository;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallResultJpaRepository;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class CallOutboxRepositoryAdapter implements CallOutboxRepository {

    private static final Logger log = LoggerFactory.getLogger(CallOutboxRepositoryAdapter.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final CallResultJpaRepository results;
    private final CallAttemptJpaRepository callAttempts;

    public CallOutboxRepositoryAdapter(CallResultJpaRepository results, CallAttemptJpaRepository callAttempts) {
        this.results = results;
        this.callAttempts = callAttempts;
    }

    @Override
    public List<PendingNote> notesAwaitingCrm(int maxAttempts, int limit) {
        try {
            return results.notesAwaitingCrm(maxAttempts, PageRequest.of(0, limit)).stream()
                    .map(row -> {
                        CallResultEntity r = (CallResultEntity) row[0];
                        long clientId = (Long) row[1];
                        return new PendingNote(r.getCall().getId(), clientId, new CallSummary(
                                r.getSummary(),
                                readOutcome(r.getOutcome()),
                                r.getSentiment(),
                                r.isNeedsFollowUp(),
                                r.getFollowUpNote()));
                    })
                    .toList();
        } catch (Exception e) {
            log.warn("Outbox scan for CRM notes failed: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<PendingSummary> attemptsAwaitingSummary(int maxAttempts, int limit) {
        try {
            return callAttempts.attemptsAwaitingSummary(maxAttempts, PageRequest.of(0, limit)).stream()
                    .map(row -> new PendingSummary((Long) row[0], (Long) row[1], (Long) row[2]))
                    .toList();
        } catch (Exception e) {
            log.warn("Outbox scan for summaries failed: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readOutcome(String outcomeJson) {
        if (outcomeJson == null || outcomeJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return JSON.readValue(outcomeJson, Map.class);
        } catch (Exception e) {
            log.warn("Corrupt call_result.outcome JSON: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    public void markCrmPosted(long callId, Long noteId) {
        try {
            results.markCrmPosted(callId, noteId);
        } catch (Exception e) {
            log.warn("markCrmPosted failed for call {}: {}", callId, e.getMessage());
        }
    }

    @Override
    public void markCrmFailed(long callId, String error) {
        try {
            results.markCrmFailed(callId, error);
        } catch (Exception e) {
            log.warn("markCrmFailed failed for call {}: {}", callId, e.getMessage());
        }
    }

    @Override
    public void countSummaryAttempt(long callId) {
        try {
            callAttempts.countSummaryAttempt(callId);
        } catch (Exception e) {
            log.warn("countSummaryAttempt failed for call {}: {}", callId, e.getMessage());
        }
    }
}
