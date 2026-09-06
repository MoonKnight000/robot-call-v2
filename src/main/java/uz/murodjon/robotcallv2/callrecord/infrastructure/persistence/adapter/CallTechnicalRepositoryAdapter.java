package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.callrecord.application.port.output.CallTechnicalRepository;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallTechnical;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallAttemptEntity;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallTechnicalEntity;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallAttemptJpaRepository;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallTechnicalJpaRepository;

import java.time.Instant;

@Component
public class CallTechnicalRepositoryAdapter implements CallTechnicalRepository {

    private final CallTechnicalJpaRepository callTechnicalJpaRepository;
    private final CallAttemptJpaRepository callAttemptJpaRepository;

    public CallTechnicalRepositoryAdapter(CallTechnicalJpaRepository callTechnicalJpaRepository,
                                          CallAttemptJpaRepository callAttemptJpaRepository) {
        this.callTechnicalJpaRepository = callTechnicalJpaRepository;
        this.callAttemptJpaRepository = callAttemptJpaRepository;
    }

    @Override
    @Transactional
    public boolean save(CallTechnical technical) {
        CallAttemptEntity attempt = callAttemptJpaRepository.findById(technical.callId()).orElse(null);
        if (attempt == null) {
            return false;
        }
        CallTechnicalEntity entity = new CallTechnicalEntity();
        entity.setCall(attempt);
        entity.setChannelName(technical.channelName());
        entity.setTrunk(technical.trunk());
        entity.setAmdResult(technical.amdResult());
        entity.setSttProvider(technical.sttProvider());
        entity.setTtsProvider(technical.ttsProvider());
        entity.setTtsVoice(technical.ttsVoice());
        entity.setLlmModel(technical.llmModel());
        entity.setPromptTokens(technical.promptTokens());
        entity.setCompletionTokens(technical.completionTokens());
        entity.setCachedTokens(technical.cachedTokens());
        entity.setTurnCount(technical.turnCount());
        entity.setAvgTurnLatencyMs(technical.avgTurnLatencyMs());
        entity.setMaxTurnLatencyMs(technical.maxTurnLatencyMs());
        entity.setAvgLlmLatencyMs(technical.avgLlmLatencyMs());
        entity.setMaxLlmLatencyMs(technical.maxLlmLatencyMs());
        entity.setCreatedAt(Instant.now());
        callTechnicalJpaRepository.save(entity);
        return true;
    }
}
