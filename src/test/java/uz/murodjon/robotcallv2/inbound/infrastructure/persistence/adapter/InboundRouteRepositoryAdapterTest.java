package uz.murodjon.robotcallv2.inbound.infrastructure.persistence.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository.AiAgentJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;
import uz.murodjon.robotcallv2.inbound.domain.enums.InboundAfterHoursAction;
import uz.murodjon.robotcallv2.inbound.infrastructure.persistence.entity.InboundRouteEntity;
import uz.murodjon.robotcallv2.inbound.infrastructure.persistence.mapper.InboundRouteEntityMapper;
import uz.murodjon.robotcallv2.inbound.infrastructure.persistence.repository.InboundRouteJpaRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InboundRouteRepositoryAdapterTest {

    private static final long COMPANY_ID = 1L;

    private InboundRouteJpaRepository jpaRepository;
    private InboundRouteEntityMapper mapper;
    private CompanyJpaRepository companyJpaRepository;
    private AiAgentJpaRepository aiAgentJpaRepository;
    private InboundRouteRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(InboundRouteJpaRepository.class);
        mapper = new InboundRouteEntityMapper();
        companyJpaRepository = mock(CompanyJpaRepository.class);
        aiAgentJpaRepository = mock(AiAgentJpaRepository.class);
        adapter = new InboundRouteRepositoryAdapter(jpaRepository, mapper, companyJpaRepository, aiAgentJpaRepository);
    }

    @Test
    void createSetsTypedDestinationCorrectlyForAiAgent() {
        CompanyEntity company = new CompanyEntity();
        company.setId(COMPANY_ID);
        when(companyJpaRepository.getReferenceById(COMPANY_ID)).thenReturn(company);

        InboundRoute route = new InboundRoute(0L, COMPANY_ID, "+998712001122", 5L,
                InboundAfterHoursAction.AI_AGENT, "88", null, null, null, null, null, null, true, null);

        ArgumentCaptor<InboundRouteEntity> captor = ArgumentCaptor.forClass(InboundRouteEntity.class);
        when(jpaRepository.save(captor.capture())).thenAnswer(inv -> {
            InboundRouteEntity saved = inv.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        long id = adapter.create(COMPANY_ID, route);

        assertThat(id).isEqualTo(10L);
        InboundRouteEntity entity = captor.getValue();
        assertThat(entity.getAfterHoursAction()).isEqualTo(InboundAfterHoursAction.AI_AGENT);
        assertThat(entity.getAfterHoursAgentId()).isEqualTo(88L);
        assertThat(entity.getAfterHoursVoicemailId()).isNull();
        assertThat(entity.getAfterHoursForwardPhone()).isNull();
        assertThat(entity.getAfterHoursDestination()).isEqualTo("88");
    }

    @Test
    void createSetsTypedDestinationCorrectlyForVoicemail() {
        CompanyEntity company = new CompanyEntity();
        company.setId(COMPANY_ID);
        when(companyJpaRepository.getReferenceById(COMPANY_ID)).thenReturn(company);

        InboundRoute route = new InboundRoute(0L, COMPANY_ID, "+998712001122", 5L,
                InboundAfterHoursAction.VOICEMAIL, "2", null, null, null, null, null, null, true, null);

        ArgumentCaptor<InboundRouteEntity> captor = ArgumentCaptor.forClass(InboundRouteEntity.class);
        when(jpaRepository.save(captor.capture())).thenAnswer(inv -> {
            InboundRouteEntity saved = inv.getArgument(0);
            saved.setId(11L);
            return saved;
        });

        long id = adapter.create(COMPANY_ID, route);

        assertThat(id).isEqualTo(11L);
        InboundRouteEntity entity = captor.getValue();
        assertThat(entity.getAfterHoursAction()).isEqualTo(InboundAfterHoursAction.VOICEMAIL);
        assertThat(entity.getAfterHoursVoicemailId()).isEqualTo(2L);
        assertThat(entity.getAfterHoursAgentId()).isNull();
        assertThat(entity.getAfterHoursDestination()).isEqualTo("2");
    }

    @Test
    void createSetsTypedDestinationCorrectlyForForwardExternal() {
        CompanyEntity company = new CompanyEntity();
        company.setId(COMPANY_ID);
        when(companyJpaRepository.getReferenceById(COMPANY_ID)).thenReturn(company);

        InboundRoute route = new InboundRoute(0L, COMPANY_ID, "+998712001122", 5L,
                InboundAfterHoursAction.FORWARD_EXTERNAL, "+998901234567", null, null, null, null, null, null, true, null);

        ArgumentCaptor<InboundRouteEntity> captor = ArgumentCaptor.forClass(InboundRouteEntity.class);
        when(jpaRepository.save(captor.capture())).thenAnswer(inv -> {
            InboundRouteEntity saved = inv.getArgument(0);
            saved.setId(12L);
            return saved;
        });

        long id = adapter.create(COMPANY_ID, route);

        assertThat(id).isEqualTo(12L);
        InboundRouteEntity entity = captor.getValue();
        assertThat(entity.getAfterHoursAction()).isEqualTo(InboundAfterHoursAction.FORWARD_EXTERNAL);
        assertThat(entity.getAfterHoursForwardPhone()).isEqualTo("+998901234567");
        assertThat(entity.getAfterHoursAgentId()).isNull();
        assertThat(entity.getAfterHoursVoicemailId()).isNull();
        assertThat(entity.getAfterHoursDestination()).isEqualTo("+998901234567");
    }

    @Test
    void findMapsTypedDestinationBackToDestinationString() {
        InboundRouteEntity entity = new InboundRouteEntity();
        entity.setId(10L);
        entity.setDidNumber("+998712001122");
        entity.setAfterHoursAction(InboundAfterHoursAction.AI_AGENT);
        entity.setAfterHoursAgentId(88L);

        when(jpaRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(entity));

        InboundRoute route = adapter.find(COMPANY_ID, 10L);

        assertThat(route).isNotNull();
        assertThat(route.afterHoursAction()).isEqualTo(InboundAfterHoursAction.AI_AGENT);
        assertThat(route.afterHoursDestination()).isEqualTo("88");
    }
}
