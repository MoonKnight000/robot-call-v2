package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.agent.dialog.DialogTechnicalSnapshot;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallTechnicalRepository;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallAttemptEntity;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallTechnicalEntity;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallAttemptJpaRepository;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallTechnicalJpaRepository;

import java.time.Instant;

@Component
public class CallTechnicalRepositoryAdapter implements CallTechnicalRepository {

    private final CallTechnicalJpaRepository jpa;
    private final CallAttemptJpaRepository callAttempts;

    public CallTechnicalRepositoryAdapter(CallTechnicalJpaRepository jpa, CallAttemptJpaRepository callAttempts) {
        this.jpa = jpa;
        this.callAttempts = callAttempts;
    }

    @Override
    @Transactional
    public boolean save(long callId, String channelName, String trunk, String amdResult, String sttProvider,
                        String ttsProvider, String ttsVoice, String llmModel, DialogTechnicalSnapshot technical) {
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
