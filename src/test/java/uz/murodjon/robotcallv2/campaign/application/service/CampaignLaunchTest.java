package uz.murodjon.robotcallv2.campaign.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.tts.TtsWarmup;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.campaign.application.dto.CampaignOverviewResponse;
import uz.murodjon.robotcallv2.campaign.application.dto.CampaignStatusResponse;
import uz.murodjon.robotcallv2.campaign.application.port.output.CallQueueLookupPort;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignTargetRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.TargetSourceRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignSequence;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTargetStats;
import uz.murodjon.robotcallv2.campaign.domain.entity.ConversationStepConfig;
import uz.murodjon.robotcallv2.campaign.domain.entity.SequenceStep;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.SequenceStepType;
import uz.murodjon.robotcallv2.campaign.domain.service.CampaignValidator;
import uz.murodjon.robotcallv2.campaign.domain.service.SequenceValidator;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.CallerIdPoolUseCase;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** What a campaign needs before it can be launched: an agent, a list, the legal consent. */
class CampaignLaunchTest {

    private static final long COMPANY_ID = 1L;
    private static final long CAMPAIGN_ID = 3L;
    private static final long AGENT_ID = 7L;

    private CampaignRepository campaigns;
    private CampaignTargetRepository targets;
    private AiAgentUseCase aiAgents;
    private CampaignService service;

    @BeforeEach
    void setUp() {
        campaigns = mock(CampaignRepository.class);
        targets = mock(CampaignTargetRepository.class);
        aiAgents = mock(AiAgentUseCase.class);
        service = new CampaignService(campaigns, targets, mock(TargetSourceRepository.class), aiAgents,
                mock(UserUseCase.class), mock(CompanyConfigUseCase.class), mock(AuditUseCase.class),
                mock(NotificationUseCase.class), mock(TtsWarmup.class), mock(CampaignTargetService.class),
                mock(CampaignOutcomeService.class), new CampaignValidator(), new SequenceValidator(),
                mock(CallerIdPoolUseCase.class), mock(CallQueueLookupPort.class), mock(AutoEnrollService.class), Clock.systemUTC());
        when(targets.countByCampaign(COMPANY_ID, CAMPAIGN_ID)).thenReturn(5L);
        when(targets.findStatsByCampaignId(anyLong(), anyLong())).thenReturn(CampaignTargetStats.ZERO);
        givenAgent(AGENT_ID, true);
    }

    private void givenAgent(long id, boolean enabled) {
        AiAgent agent = mock(AiAgent.class);
        when(agent.enabled()).thenReturn(enabled);
        when(aiAgents.requireAgent(COMPANY_ID, id)).thenReturn(agent);
    }

    private Campaign givenCampaign(CampaignStatus status, Long agentId, Instant consent, CampaignSequence sequence) {
        Campaign campaign = Campaign.builder().id(CAMPAIGN_ID).name("c").status(status).companyId(COMPANY_ID)
                .aiAgentId(agentId).consentConfirmedAt(consent).sequence(sequence).build();
        when(campaigns.find(COMPANY_ID, CAMPAIGN_ID)).thenReturn(campaign);
        return campaign;
    }

    private static SequenceStep conversation(String id, Long agentId) {
        return new SequenceStep(id, SequenceStepType.CONVERSATION, id, new ConversationStepConfig(agentId, 3, 0),
                null, null, Map.of(), null, null);
    }

    @Test
    void aDraftIsNotLaunchedBeforeTheConsentIsConfirmed() {
        givenCampaign(CampaignStatus.DRAFT, AGENT_ID, null, null);

        assertThatThrownBy(() -> service.start(COMPANY_ID, CAMPAIGN_ID, true))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CAMPAIGN_CONSENT_REQUIRED));
        verify(campaigns, never()).updateStatus(anyLong(), anyLong(), any());
    }

    @Test
    void aDraftWithTheConsentConfirmedIsLaunched() {
        givenCampaign(CampaignStatus.DRAFT, AGENT_ID, Instant.parse("2026-10-31T10:00:00Z"), null);

        CampaignStatusResponse started = service.start(COMPANY_ID, CAMPAIGN_ID, true);

        assertThat(started.status()).isEqualTo(CampaignStatus.ACTIVE);
    }

    @Test
    void aPausedCampaignIsResumedWithoutAskingForTheConsentAgain() {
        givenCampaign(CampaignStatus.PAUSED, AGENT_ID, null, null);

        assertThat(service.start(COMPANY_ID, CAMPAIGN_ID, true).status()).isEqualTo(CampaignStatus.ACTIVE);
    }

    @Test
    void aCampaignWithoutAnAgentCannotBeLaunched() {
        givenCampaign(CampaignStatus.DRAFT, null, Instant.now(), null);

        assertThatThrownBy(() -> service.start(COMPANY_ID, CAMPAIGN_ID, true))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CAMPAIGN_AI_AGENT_REQUIRED));
    }

    @Test
    void everyConversationStepNeedsAnAgentToLaunch() {
        givenCampaign(CampaignStatus.DRAFT, AGENT_ID, Instant.now(),
                new CampaignSequence("c1", List.of(conversation("c1", AGENT_ID), conversation("c2", null))));

        assertThatThrownBy(() -> service.start(COMPANY_ID, CAMPAIGN_ID, true))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CAMPAIGN_SEQUENCE_INVALID));
    }

    @Test
    void aDisabledAgentOfAStepBlocksTheLaunch() {
        givenAgent(8L, false);
        givenCampaign(CampaignStatus.DRAFT, AGENT_ID, Instant.now(),
                new CampaignSequence("c1", List.of(conversation("c1", AGENT_ID), conversation("c2", 8L))));

        assertThatThrownBy(() -> service.start(COMPANY_ID, CAMPAIGN_ID, true))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.AI_AGENT_DISABLED));
    }

    @Test
    void confirmingTheConsentTwiceStampsItOnce() {
        givenCampaign(CampaignStatus.DRAFT, AGENT_ID, null, null);

        service.acceptConsent(COMPANY_ID, CAMPAIGN_ID);
        givenCampaign(CampaignStatus.DRAFT, AGENT_ID, Instant.now(), null);
        service.acceptConsent(COMPANY_ID, CAMPAIGN_ID);

        verify(campaigns).recordConsent(anyLong(), anyLong(), any());
    }

    @Test
    void theOverviewNamesWhatBlocksTheLaunchWithoutFailing() {
        givenCampaign(CampaignStatus.DRAFT, null, null, null);

        CampaignOverviewResponse overview = service.requireCampaignOverview(COMPANY_ID, CAMPAIGN_ID);

        assertThat(overview.launchBlocker().code()).isEqualTo("CAMPAIGN_AI_AGENT_REQUIRED");
        assertThat(overview.callerIdPoolName()).isNull();
        assertThat(overview.callerIdNumberCount()).isZero();
    }

    @Test
    void theOverviewOfALaunchableCampaignHasNoBlocker() {
        givenCampaign(CampaignStatus.DRAFT, AGENT_ID, null, null);

        assertThat(service.requireCampaignOverview(COMPANY_ID, CAMPAIGN_ID).launchBlocker()).isNull();
    }
}
