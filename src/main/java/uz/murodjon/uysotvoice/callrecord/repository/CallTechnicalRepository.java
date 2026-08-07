package uz.murodjon.uysotvoice.callrecord.repository;

import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.agent.dialog.DialogTechnicalSnapshot;
import uz.murodjon.uysotvoice.callrecord.entity.CallAttemptEntity;
import uz.murodjon.uysotvoice.callrecord.entity.CallTechnicalEntity;

import java.time.Instant;

/**
 * JPA-backed DAO for {@code call_technical} (§10.5 "Texnik" tab). Written once, from
 * {@code CallFinalizer} at teardown — unlike {@code call_result}, nothing re-runs this
 * later, so a plain insert is enough.
 */
@Repository
public class CallTechnicalRepository {

    private final CallTechnicalJpaRepository jpa;
    private final CallAttemptJpaRepository callAttempts;

    public CallTechnicalRepository(CallTechnicalJpaRepository jpa, CallAttemptJpaRepository callAttempts) {
        this.jpa = jpa;
        this.callAttempts = callAttempts;
    }

    /** {@code false} if {@code callId} has no attempt row (nothing to attach to) — caller skips the write. */
    public boolean save(long callId, String channelName, String trunk, String amdResult, String sttProvider,
                        String ttsProvider, String ttsVoice, String llmModel, DialogTechnicalSnapshot technical) {
        // Loaded, not getReferenceById: call_technical derives its own id from this
        // association (@MapsId), and Hibernate refuses to read an identifier out of an
        // uninitialized proxy on persist ("uninitialized proxy passed to persist()").
        CallAttemptEntity attempt = callAttempts.findById(callId).orElse(null);
        if (attempt == null) {
            return false;
        }
        CallTechnicalEntity entity = new CallTechnicalEntity();
        entity.setCall(attempt);
        entity.setChannelName(channelName);
        entity.setTrunk(trunk);
        entity.setAmdResult(amdResult);
        entity.setSttProvider(sttProvider);
        entity.setTtsProvider(ttsProvider);
        entity.setTtsVoice(ttsVoice);
        entity.setLlmModel(llmModel);
        if (technical != null) {
            entity.setPromptTokens((int) technical.promptTokens());
            entity.setCompletionTokens((int) technical.completionTokens());
            entity.setCachedTokens((int) technical.cachedTokens());
            entity.setTurnCount(technical.turnCount());
            entity.setAvgTurnLatencyMs(technical.avgTurnLatencyMs());
            entity.setMaxTurnLatencyMs(technical.maxTurnLatencyMs());
            entity.setAvgLlmLatencyMs(technical.avgLlmLatencyMs());
            entity.setMaxLlmLatencyMs(technical.maxLlmLatencyMs());
        }
        entity.setCreatedAt(Instant.now());
        jpa.save(entity);
        return true;
    }
}
