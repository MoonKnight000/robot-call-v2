package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentEntity;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallAttempt;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallAttemptEntity;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallAttemptJpaRepository;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignTargetEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignVariantEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository.CampaignTargetJpaRepository;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository.CampaignVariantJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.inbound.infrastructure.persistence.repository.InboundRouteJpaRepository;
import uz.murodjon.robotcallv2.storage.infrastructure.persistence.repository.StoredFileJpaRepository;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.repository.UserJpaRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CallAttemptRepositoryAdapterTest {

    private static final long COMPANY_ID = 1L;
    private static final long CALL_ID = 500L;
    private static final long AGENT_ID = 12L;
    private static final int AGENT_VERSION = 2;

    private CallAttemptJpaRepository callAttemptJpaRepository;
    private CampaignTargetJpaRepository campaignTargetJpaRepository;
    private CompanyJpaRepository companyJpaRepository;
    private InboundRouteJpaRepository inboundRouteJpaRepository;
    private UserJpaRepository userJpaRepository;
    private StoredFileJpaRepository storedFileJpaRepository;
    private CampaignVariantJpaRepository campaignVariantJpaRepository;
    private CallAttemptRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        callAttemptJpaRepository = mock(CallAttemptJpaRepository.class);
        campaignTargetJpaRepository = mock(CampaignTargetJpaRepository.class);
        companyJpaRepository = mock(CompanyJpaRepository.class);
        inboundRouteJpaRepository = mock(InboundRouteJpaRepository.class);
        userJpaRepository = mock(UserJpaRepository.class);
        storedFileJpaRepository = mock(StoredFileJpaRepository.class);
        campaignVariantJpaRepository = mock(CampaignVariantJpaRepository.class);

        adapter = new CallAttemptRepositoryAdapter(
                callAttemptJpaRepository,
                campaignTargetJpaRepository,
                companyJpaRepository,
                inboundRouteJpaRepository,
                userJpaRepository,
                storedFileJpaRepository,
                campaignVariantJpaRepository
        );
    }

    @Test
    void recordAgentDelegatesToJpaRepository() {
        adapter.recordAgent(CALL_ID, AGENT_ID, AGENT_VERSION);

        verify(callAttemptJpaRepository).recordAgent(CALL_ID, AGENT_ID, AGENT_VERSION);
    }

    @Test
    void callAttemptEntityAiAgentAssociationWorksCorrectly() {
        CallAttemptEntity entity = new CallAttemptEntity();
        assertThat(entity.getAiAgent()).isNull();
        assertThat(entity.getAiAgentId()).isNull();

        AiAgentEntity agent = new AiAgentEntity();
        try {
            var field = AiAgentEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(agent, AGENT_ID);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        entity.setAiAgent(agent);
        entity.setAgentVersion(AGENT_VERSION);

        assertThat(entity.getAiAgent()).isSameAs(agent);
        assertThat(entity.getAiAgentId()).isEqualTo(AGENT_ID);
        assertThat(entity.getAgentVersion()).isEqualTo(AGENT_VERSION);

        entity.setAiAgent(null);
        assertThat(entity.getAiAgent()).isNull();
        assertThat(entity.getAiAgentId()).isNull();
    }

    @Test
    void createPersistsEntityWithCompanyAndTargetReferences() {
        CompanyEntity company = new CompanyEntity();
        when(companyJpaRepository.getReferenceById(COMPANY_ID)).thenReturn(company);

        CampaignTargetEntity target = new CampaignTargetEntity();
        when(campaignTargetJpaRepository.getReferenceById(10L)).thenReturn(target);

        CallAttemptEntity savedEntity = new CallAttemptEntity();
        savedEntity.setId(CALL_ID);
        when(callAttemptJpaRepository.save(any(CallAttemptEntity.class))).thenReturn(savedEntity);

        CallAttempt attempt = CallAttempt.starting(COMPANY_ID, 10L, "channel-1", "+998901234567", "uz-UZ", null, null);
        long id = adapter.create(attempt);

        assertThat(id).isEqualTo(CALL_ID);

        ArgumentCaptor<CallAttemptEntity> captor = ArgumentCaptor.forClass(CallAttemptEntity.class);
        verify(callAttemptJpaRepository).save(captor.capture());
        CallAttemptEntity captured = captor.getValue();
        assertThat(captured.getTarget()).isSameAs(target);
        assertThat(captured.getAsteriskChannel()).isEqualTo("channel-1");
        assertThat(captured.getPhone()).isEqualTo("+998901234567");
        assertThat(captured.getLanguage()).isEqualTo("uz-UZ");
    }

    @Test
    void findByCompanyIdAndIdMapsEntityToDomain() {
        CallAttemptEntity entity = new CallAttemptEntity();
        entity.setId(CALL_ID);
        CompanyEntity company = new CompanyEntity();
        try {
            var field = CompanyEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(company, COMPANY_ID);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        entity.setCompany(company);
        entity.setPhone("+998901234567");
        entity.setLanguage("uz-UZ");

        when(callAttemptJpaRepository.findByIdAndCompanyId(CALL_ID, COMPANY_ID))
                .thenReturn(Optional.of(entity));

        CallAttempt domain = adapter.findByCompanyIdAndId(COMPANY_ID, CALL_ID);

        assertThat(domain).isNotNull();
        assertThat(domain.id()).isEqualTo(CALL_ID);
        assertThat(domain.companyId()).isEqualTo(COMPANY_ID);
        assertThat(domain.phone()).isEqualTo("+998901234567");
        assertThat(domain.language()).isEqualTo("uz-UZ");
    }
}
