package uz.murodjon.robotcallv2.quality.infrastructure.persistence.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentEntity;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallAttemptEntity;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallAttemptJpaRepository;
import uz.murodjon.robotcallv2.quality.domain.entity.CallQuality;
import uz.murodjon.robotcallv2.quality.domain.entity.ScoredAgent;
import uz.murodjon.robotcallv2.quality.domain.enums.CallReviewStatus;
import uz.murodjon.robotcallv2.quality.infrastructure.persistence.entity.CallQualityEntity;
import uz.murodjon.robotcallv2.quality.infrastructure.persistence.mapper.CallQualityEntityMapper;
import uz.murodjon.robotcallv2.quality.infrastructure.persistence.repository.CallQualityJpaRepository;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CallQualityRepositoryAdapterTest {

    private static final long COMPANY_ID = 1L;
    private static final long CALL_ID = 200L;
    private static final long AGENT_ID = 15L;
    private static final int AGENT_VERSION = 3;

    private CallQualityJpaRepository jpaRepository;
    private CallAttemptJpaRepository callAttemptJpaRepository;
    private CallQualityEntityMapper mapper;
    private CallQualityRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(CallQualityJpaRepository.class);
        callAttemptJpaRepository = mock(CallAttemptJpaRepository.class);
        mapper = new CallQualityEntityMapper();
        adapter = new CallQualityRepositoryAdapter(jpaRepository, callAttemptJpaRepository, mapper);
    }

    @Test
    void saveLinksCallAttemptReference() {
        CallAttemptEntity attemptRef = new CallAttemptEntity();
        attemptRef.setId(CALL_ID);
        when(callAttemptJpaRepository.getReferenceById(CALL_ID)).thenReturn(attemptRef);

        CallQualityEntity savedEntity = new CallQualityEntity();
        savedEntity.setId(10L);
        savedEntity.setCompanyId(COMPANY_ID);
        savedEntity.setCallAttempt(attemptRef);
        savedEntity.setScore(85);
        when(jpaRepository.save(any(CallQualityEntity.class))).thenReturn(savedEntity);

        CallQuality domain = new CallQuality(
                null, COMPANY_ID, CALL_ID, AGENT_ID, AGENT_VERSION, Disposition.COMPLETED,
                85, false, false, false, true, true, false, false,
                "Good call", false, 6, 0, 1, 0, 0, 0, false,
                CallReviewStatus.PENDING, null, null, null, null, null, null
        );

        CallQuality result = adapter.save(domain);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.callAttemptId()).isEqualTo(CALL_ID);

        ArgumentCaptor<CallQualityEntity> captor = ArgumentCaptor.forClass(CallQualityEntity.class);
        verify(jpaRepository).save(captor.capture());
        CallQualityEntity captured = captor.getValue();
        assertThat(captured.getCallAttempt()).isSameAs(attemptRef);
        assertThat(captured.getScore()).isEqualTo(85);
    }

    @Test
    void findByCompanyIdAndCallAttemptIdResolvesNormalizedAttemptFields() {
        AiAgentEntity agent = new AiAgentEntity();
        try {
            var idField = AiAgentEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(agent, AGENT_ID);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        CallAttemptEntity attempt = new CallAttemptEntity();
        attempt.setId(CALL_ID);
        attempt.setAiAgent(agent);
        attempt.setAgentVersion(AGENT_VERSION);
        attempt.setDisposition(Disposition.PROMISE_TO_PAY);

        CallQualityEntity entity = new CallQualityEntity();
        entity.setId(1L);
        entity.setCompanyId(COMPANY_ID);
        entity.setCallAttempt(attempt);
        entity.setScore(92);

        when(jpaRepository.findByCompanyIdAndCallAttemptId(COMPANY_ID, CALL_ID))
                .thenReturn(Optional.of(entity));

        CallQuality domain = adapter.findByCompanyIdAndCallAttemptId(COMPANY_ID, CALL_ID);

        assertThat(domain).isNotNull();
        assertThat(domain.id()).isEqualTo(1L);
        assertThat(domain.callAttemptId()).isEqualTo(CALL_ID);
        assertThat(domain.aiAgentId()).isEqualTo(AGENT_ID);
        assertThat(domain.agentVersion()).isEqualTo(AGENT_VERSION);
        assertThat(domain.disposition()).isEqualTo(Disposition.PROMISE_TO_PAY);
        assertThat(domain.score()).isEqualTo(92);
    }

    @Test
    void findAgentsScoredSinceMapsGroupedProjection() {
        Instant since = Instant.now().minusSeconds(86400);
        Object[] row1 = new Object[]{COMPANY_ID, AGENT_ID, 10L};
        when(jpaRepository.countByAgentSince(since)).thenReturn(List.of(row1));

        List<ScoredAgent> result = adapter.findAgentsScoredSince(since);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).companyId()).isEqualTo(COMPANY_ID);
        assertThat(result.get(0).aiAgentId()).isEqualTo(AGENT_ID);
        assertThat(result.get(0).calls()).isEqualTo(10L);
    }
}
