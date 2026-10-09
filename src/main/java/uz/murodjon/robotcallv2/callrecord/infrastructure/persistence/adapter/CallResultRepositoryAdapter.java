package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.callrecord.application.port.output.CallResultRepository;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallReanalysisScope;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallReanalysisTarget;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallResult;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallReanalysisScopeProjection;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallResultJpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class CallResultRepositoryAdapter implements CallResultRepository {

    private static final TypeReference<Map<String, Object>> OUTCOME_TYPE = new TypeReference<>() {};

    private final CallResultJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;

    public CallResultRepositoryAdapter(CallResultJpaRepository jpaRepository, ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void insertIgnoringConflict(CallResult result) {
        jpaRepository.insertIgnoringConflict(
                result.callId(),
                result.summary(),
                result.reasonCode() == null ? null : result.reasonCode().name(),
                result.promisedDate(),
                result.promisedAmount(),
                result.sentiment() == null ? null : result.sentiment().name(),
                result.needsFollowUp(),
                result.followUpNote(),
                result.escalated(),
                result.crmNoteId(),
                result.outcome());
    }

    @Override
    public void markCrmPosted(long callId, String crmNoteId) {
        jpaRepository.markCrmPosted(callId, crmNoteId);
    }

    @Override
    public int deleteCreatedBefore(Instant cutoff, int batchSize) {
        return jpaRepository.deleteCreatedBefore(cutoff, batchSize);
    }

    @Override
    public List<CallReanalysisTarget> findReanalysisTargets(long companyId, long aiAgentId, Instant from, Instant to,
                                                            long afterCallId, int limit) {
        return jpaRepository.findReanalysisTargets(companyId, aiAgentId, from, to, afterCallId, PageRequest.of(0, limit))
                .stream()
                .map(target -> new CallReanalysisTarget(target.getId(), target.getStartedAt()))
                .toList();
    }

    @Override
    public CallReanalysisScope findReanalysisScope(long companyId, long aiAgentId, Instant from, Instant to) {
        CallReanalysisScopeProjection scope = jpaRepository.summarizeReanalysisScope(companyId, aiAgentId, from, to);
        return new CallReanalysisScope(scope.getCallCount(), scope.getTranscriptChars());
    }

    @Override
    public Map<String, Object> findOutcomeByCallId(long callId) {
        return jpaRepository.findOutcomeJsonByCallId(callId).map(this::readOutcome).orElse(null);
    }

    @Override
    public void updateOutcome(long callId, Map<String, Object> outcome) {
        try {
            jpaRepository.updateOutcome(callId, objectMapper.writeValueAsString(outcome));
        } catch (Exception e) {
            throw new IllegalStateException("Call outcome could not be serialised", e);
        }
    }

    private Map<String, Object> readOutcome(String json) {
        try {
            return objectMapper.readValue(json, OUTCOME_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Stored call outcome could not be read", e);
        }
    }
}
