package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallTalkMetricsRepository;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallTalkMetrics;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallAttemptEntity;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallTalkMetricsEntity;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallAttemptJpaRepository;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallTalkMetricsJpaRepository;

import java.time.Instant;

@Component
public class CallTalkMetricsRepositoryAdapter implements CallTalkMetricsRepository {

    private final CallTalkMetricsJpaRepository callTalkMetricsJpaRepository;
    private final CallAttemptJpaRepository callAttemptJpaRepository;

    public CallTalkMetricsRepositoryAdapter(CallTalkMetricsJpaRepository callTalkMetricsJpaRepository,
                                            CallAttemptJpaRepository callAttemptJpaRepository) {
        this.callTalkMetricsJpaRepository = callTalkMetricsJpaRepository;
        this.callAttemptJpaRepository = callAttemptJpaRepository;
    }

    @Override
    @Transactional
    public boolean upsert(CallTalkMetrics metrics) {
        CallAttemptEntity attempt = callAttemptJpaRepository.findById(metrics.callId()).orElse(null);
        if (attempt == null) {
            return false;
        }
        CallTalkMetricsEntity entity = callTalkMetricsJpaRepository.findById(metrics.callId())
                .orElseGet(CallTalkMetricsEntity::new);
        entity.setCall(attempt);
        entity.setAnalysedMs(metrics.analysedMs());
        entity.setClientTalkMs(metrics.clientTalkMs());
        entity.setOperatorTalkMs(metrics.operatorTalkMs());
        entity.setOvertalkMs(metrics.overtalkMs());
        entity.setSilenceMs(metrics.silenceMs());
        entity.setLongestSilenceMs(metrics.longestSilenceMs());
        entity.setCreatedAt(Instant.now());
        callTalkMetricsJpaRepository.save(entity);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public CallTalkMetrics findByCallId(long callId) {
        return callTalkMetricsJpaRepository.findById(callId)
                .map(entity -> new CallTalkMetrics(entity.getId(), entity.getAnalysedMs(), entity.getClientTalkMs(),
                        entity.getOperatorTalkMs(), entity.getOvertalkMs(), entity.getSilenceMs(),
                        entity.getLongestSilenceMs()))
                .orElse(null);
    }
}
