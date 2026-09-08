package uz.murodjon.robotcallv2.campaign.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.tts.TtsWarmup;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignTargetRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.DebtorSourcePort;
import uz.murodjon.robotcallv2.campaign.application.port.output.TargetSourceRepository;
import uz.murodjon.robotcallv2.campaign.application.service.CampaignService;
import uz.murodjon.robotcallv2.campaign.application.service.ChainedTargetImporter;
import uz.murodjon.robotcallv2.campaign.application.service.TargetApiImporter;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignFilter;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTargetStats;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignType;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;
import uz.murodjon.robotcallv2.campaign.domain.service.CampaignValidator;
import uz.murodjon.robotcallv2.company.application.service.CompanyConfigService;
import uz.murodjon.robotcallv2.dialer.infrastructure.config.DialerProperties;
import uz.murodjon.robotcallv2.dialer.infrastructure.config.RetryProperties;
import uz.murodjon.robotcallv2.donotcall.application.port.output.DoNotCallRepository;
import uz.murodjon.robotcallv2.notification.application.service.NotificationService;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.util.SecretCipher;
import uz.murodjon.robotcallv2.user.application.service.UserService;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class CampaignServiceTest {

    private static final long CAMPAIGN_ID = 3L;
    private static final long AI_AGENT_ID = 7L;
    private static final long TARGET_ID = 42L;

    private CampaignRepository campaigns;
    private CampaignTargetRepository targets;
    private DoNotCallRepository doNotCallList;
    private AiAgentUseCase aiAgents;
    private UserService users;
    private CompanyConfigService companyConfig;
    private DialerProperties dialerProps;
    private AuditService audit;
    private NotificationService notifications;
    private TtsWarmup ttsWarmup;
    private TargetSourceRepository targetSources;
    private Clock clock;
    private CampaignService service;

    @BeforeEach
    void setUp() {
        campaigns = mock(CampaignRepository.class);
        targets = mock(CampaignTargetRepository.class);
        doNotCallList = mock(DoNotCallRepository.class);
        aiAgents = mock(AiAgentUseCase.class);
        targetSources = mock(TargetSourceRepository.class);
        users = mock(UserService.class);
        companyConfig = mock(CompanyConfigService.class);
        dialerProps = new DialerProperties(true, 5, 10, 5, 3, 60, new RetryProperties(180, 15, 1200, true));
        audit = mock(AuditService.class);
        notifications = mock(NotificationService.class);
        ttsWarmup = mock(TtsWarmup.class);
        clock = Clock.systemDefaultZone();

        service = new CampaignService(campaigns, targets, doNotCallList, aiAgents,
                users, companyConfig, dialerProps, audit, notifications, ttsWarmup,
                targetSources, mock(TargetApiImporter.class), mock(ChainedTargetImporter.class),
                mock(DebtorSourcePort.class), new CampaignValidator(), mock(SecretCipher.class), clock);
    }

    private Campaign campaign(int maxAttempts, int retryIntervalMinutes) {
        return new Campaign(
                CAMPAIGN_ID, "c", CampaignType.DEBT_COLLECTION, CampaignStatus.ACTIVE,
                LocalTime.of(9, 0), LocalTime.of(20, 0), Set.of(DayOfWeek.MONDAY), maxAttempts,
                retryIntervalMinutes, 5, 0, AI_AGENT_ID, 1L, null,
                RecurrenceType.ONCE, null, null, false, null);
    }

    @Test
    void filterCampaignsDelegatesToRepositoryWithConsistentPagingAndEnrichesTargetStats() {
        Campaign c = campaign(3, 0);
        CampaignFilter filter = new CampaignFilter(0, 10, null, "test", CampaignStatus.ACTIVE,
                CampaignType.DEBT_COLLECTION, 1L, null, null, null, null);
        when(campaigns.findAll(1L, filter)).thenReturn(List.of(c));
        when(campaigns.count(1L, filter)).thenReturn(1L);
        when(targets.statsByCampaignIds(1L, Set.of(CAMPAIGN_ID))).thenReturn(
                Map.of(CAMPAIGN_ID, new CampaignTargetStats(50, 20, 30, 15)));

        var result = service.filterCampaigns(1L, filter);

        assertThat(result.data()).hasSize(1);
        var row = result.data().getFirst();
        assertThat(row.id()).isEqualTo(CAMPAIGN_ID);
        assertThat(row.totalTargets()).isEqualTo(50L);
        assertThat(row.calledTargets()).isEqualTo(20L);
        assertThat(row.pendingTargets()).isEqualTo(30L);
        assertThat(row.completedTargets()).isEqualTo(15L);
        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.currentPage()).isZero();
    }

    @Test
    void campaignRowEnrichesTargetStats() {
        Campaign c = campaign(3, 0);
        when(campaigns.find(1L, CAMPAIGN_ID)).thenReturn(c);
        when(targets.statsByCampaignId(1L, CAMPAIGN_ID)).thenReturn(
                new CampaignTargetStats(100, 45, 55, 30));

        var row = service.campaignRow(1L, CAMPAIGN_ID);

        assertThat(row.id()).isEqualTo(CAMPAIGN_ID);
        assertThat(row.totalTargets()).isEqualTo(100L);
        assertThat(row.calledTargets()).isEqualTo(45L);
        assertThat(row.pendingTargets()).isEqualTo(55L);
        assertThat(row.completedTargets()).isEqualTo(30L);
    }

    @Test
    void isNullSafeAboutMissingCampaign() {
        when(targets.find(1L, TARGET_ID)).thenReturn(new CampaignTarget(
                TARGET_ID, CAMPAIGN_ID, 1L, "998901112233", null, "{}", TargetStatus.IN_PROGRESS, 5, false));
        when(campaigns.find(1L, CAMPAIGN_ID)).thenReturn(null);

        service.applyOutcome(1L, TARGET_ID, Disposition.NO_ANSWER);

        // Falls back to maxAttempts=3, and 5 attempts is already past it.
        verify(targets).updateStatus(eq(TARGET_ID), eq(TargetStatus.EXHAUSTED), isNull());
    }
}
