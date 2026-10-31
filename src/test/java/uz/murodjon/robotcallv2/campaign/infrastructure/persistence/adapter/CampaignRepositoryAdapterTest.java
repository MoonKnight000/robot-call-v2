package uz.murodjon.robotcallv2.campaign.infrastructure.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository.AiAgentJpaRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignDialing;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignLimits;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignNumbers;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignRecurrence;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignSchedule;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.DialMode;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetOrdering;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.mapper.CampaignEntityMapper;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository.CampaignJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.repository.UserJpaRepository;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignRepositoryAdapterTest {

    @Mock
    private CampaignJpaRepository campaignJpaRepository;

    @Mock
    private CompanyJpaRepository companyJpaRepository;

    @Mock
    private UserJpaRepository userJpaRepository;

    @Mock
    private AiAgentJpaRepository aiAgentJpaRepository;

    private CampaignEntityMapper mapper;
    private CampaignRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        mapper = new CampaignEntityMapper(new ObjectMapper());
        adapter = new CampaignRepositoryAdapter(
                campaignJpaRepository,
                companyJpaRepository,
                userJpaRepository,
                aiAgentJpaRepository,
                mapper
        );
    }

    @Test
    void create_mapsSubConfigFieldsAcrossSecondaryTables() {
        long companyId = 1L;
        CompanyEntity company = new CompanyEntity();
        company.setId(companyId);
        when(companyJpaRepository.getReferenceById(companyId)).thenReturn(company);

        Campaign campaign = Campaign.builder()
                .name("Autumn Outreach")
                .companyId(companyId)
                .status(CampaignStatus.DRAFT)
                .dialing(new CampaignDialing(DialMode.AI, null, 25, TargetOrdering.FIFO))
                .schedule(new CampaignSchedule(Instant.now(), Instant.now().plusSeconds(86400),
                        LocalTime.of(9, 0), LocalTime.of(18, 0), Set.of(DayOfWeek.MONDAY)))
                .limits(new CampaignLimits(15, 300, 5000000L, 500000L, 10))
                .recurrence(new CampaignRecurrence(RecurrenceType.DAILY, null, null, false))
                .numbers(new CampaignNumbers(null, true, 42L))
                .build();

        CampaignEntity saved = new CampaignEntity();
        saved.setId(100L);
        when(campaignJpaRepository.save(any(CampaignEntity.class))).thenReturn(saved);

        long createdId = adapter.create(companyId, campaign);

        assertThat(createdId).isEqualTo(100L);

        ArgumentCaptor<CampaignEntity> captor = ArgumentCaptor.forClass(CampaignEntity.class);
        verify(campaignJpaRepository).save(captor.capture());
        CampaignEntity entity = captor.getValue();

        assertThat(entity.getName()).isEqualTo("Autumn Outreach");
        // Secondary table campaign_dialer_config fields
        assertThat(entity.getDialMode()).isEqualTo(DialMode.AI);
        assertThat(entity.getMaxAiOverflowPercent()).isEqualTo(25);
        assertThat(entity.getMaxConcurrentCalls()).isEqualTo(15);
        assertThat(entity.getDailyCallCap()).isEqualTo(300);
        assertThat(entity.getMaxCps()).isEqualTo(10);
        assertThat(entity.isLocalPresence()).isTrue();
        assertThat(entity.getFromNumberId()).isEqualTo(42L);
        assertThat(entity.getBudgetUzs()).isEqualTo(5000000L);
        assertThat(entity.getDailyBudgetUzs()).isEqualTo(500000L);

        // Secondary table campaign_schedule fields
        assertThat(entity.getDialWindowStart()).isEqualTo(LocalTime.of(9, 0));
        assertThat(entity.getDialWindowEnd()).isEqualTo(LocalTime.of(18, 0));
        assertThat(entity.getRecurrenceType()).isEqualTo(RecurrenceType.DAILY);
    }

    @Test
    void find_mapsEntityToDomainWithAllSubConfigs() {
        long companyId = 1L;
        long campaignId = 100L;

        CompanyEntity company = new CompanyEntity();
        company.setId(companyId);

        CampaignEntity entity = new CampaignEntity();
        entity.setId(campaignId);
        entity.setCompany(company);
        entity.setName("Customer Survey");
        entity.setStatus(CampaignStatus.ACTIVE);
        entity.setDialMode(DialMode.PREDICTIVE);
        entity.setMaxConcurrentCalls(20);
        entity.setDailyCallCap(500);
        entity.setMaxCps(8);
        entity.setDialWindowStart(LocalTime.of(10, 0));
        entity.setDialWindowEnd(LocalTime.of(20, 0));
        entity.setRecurrenceType(RecurrenceType.WEEKLY);

        when(campaignJpaRepository.findByIdAndCompanyId(campaignId, companyId)).thenReturn(Optional.of(entity));

        Campaign result = adapter.find(companyId, campaignId);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(campaignId);
        assertThat(result.name()).isEqualTo("Customer Survey");
        assertThat(result.status()).isEqualTo(CampaignStatus.ACTIVE);
        assertThat(result.dialing().mode()).isEqualTo(DialMode.PREDICTIVE);
        assertThat(result.limits().maxConcurrentCalls()).isEqualTo(20);
        assertThat(result.limits().dailyCallCap()).isEqualTo(500);
        assertThat(result.limits().maxCps()).isEqualTo(8);
        assertThat(result.schedule().dialWindowStart()).isEqualTo(LocalTime.of(10, 0));
        assertThat(result.schedule().dialWindowEnd()).isEqualTo(LocalTime.of(20, 0));
        assertThat(result.recurrence().type()).isEqualTo(RecurrenceType.WEEKLY);
    }
}
