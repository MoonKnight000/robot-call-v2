package uz.murodjon.robotcallv2.campaign.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.output.AutoEnrollContactRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignTargetRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.OutcomeClassifierPort;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignAutoRetries;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignSchedule;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignSequence;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.ConversationStepConfig;
import uz.murodjon.robotcallv2.campaign.domain.entity.OutcomeContactUpdate;
import uz.murodjon.robotcallv2.campaign.domain.entity.OutcomeRoute;
import uz.murodjon.robotcallv2.campaign.domain.entity.SequenceOutcome;
import uz.murodjon.robotcallv2.campaign.domain.entity.SequenceStep;
import uz.murodjon.robotcallv2.campaign.domain.entity.StepOutcomes;
import uz.murodjon.robotcallv2.campaign.domain.entity.StepAttempt;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.DelayUnit;
import uz.murodjon.robotcallv2.campaign.domain.enums.OutcomeEvaluation;
import uz.murodjon.robotcallv2.campaign.domain.enums.OutcomeExit;
import uz.murodjon.robotcallv2.campaign.domain.enums.SequenceStepType;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;
import uz.murodjon.robotcallv2.campaign.infrastructure.config.RetryProperties;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.donotcall.application.port.input.DoNotCallUseCase;
import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallSource;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.schedule.application.port.input.BusinessScheduleUseCase;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.sms.application.dto.SmsSendRequest;
import uz.murodjon.robotcallv2.sms.application.port.input.SmsUseCase;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** What a finished call does to a target of a campaign that has a sequence with outcomes. */
class CampaignOutcomeSequenceTest {

    private static final long COMPANY_ID = 1L;
    private static final long CAMPAIGN_ID = 2L;
    private static final long TARGET_ID = 3L;
    private static final String PHONE = "998901234567";
    private static final String TRANSCRIPT = "AGENT: Salom\nUSER: Ertaga to'layman";

    private CampaignRepository campaigns;
    private CampaignTargetRepository targets;
    private SmsUseCase sms;
    private OutcomeClassifierPort classifier;
    private AutoEnrollContactRepository contacts;
    private DoNotCallUseCase doNotCall;
    private CampaignOutcomeService service;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaigns = mock(CampaignRepository.class);
        targets = mock(CampaignTargetRepository.class);
        sms = mock(SmsUseCase.class);
        classifier = mock(OutcomeClassifierPort.class);
        contacts = mock(AutoEnrollContactRepository.class);
        doNotCall = mock(DoNotCallUseCase.class);
        when(sms.isEnabled()).thenReturn(true);
        service = new CampaignOutcomeService(campaigns, targets, mock(CompanyConfigUseCase.class),
                mock(BusinessScheduleUseCase.class),
                new RetryProperties(180, 15, 1200, false), mock(AuditUseCase.class),
                mock(NotificationUseCase.class), sms, classifier, contacts, doNotCall, new ObjectMapper(),
                Clock.systemUTC());
        // c1 (2 attempts): No Response -> text -> wait an hour -> c2; its own "Callback" outcome -> c2 after 30
        // minutes; the defaults are global (Success ends successful, Do Not Call lists the number).
        SequenceOutcome callback = new SequenceOutcome("callback", "Callback", OutcomeEvaluation.PROMPT,
                "The lead asked to be called later.", null, null, new OutcomeContactUpdate(false, List.of("callback")),
                new OutcomeRoute(OutcomeExit.STEP, "c2", 30));
        SequenceStep c1 = new SequenceStep("c1", SequenceStepType.CONVERSATION, "Conversation 1",
                new ConversationStepConfig(10L, List.of(StepAttempt.createImmediate(), new StepAttempt(3, DelayUnit.DAYS))), null, null,
                new StepOutcomes(List.of(callback), null, new OutcomeRoute(OutcomeExit.STEP, "sms", 0)), null, null);
        SequenceStep text = new SequenceStep("sms", SequenceStepType.SMS, "SMS", null, "Salom", null, null, "wait", null);
        SequenceStep wait = new SequenceStep("wait", SequenceStepType.WAIT, "Wait", null, null, 60, null, "c2", null);
        SequenceStep c2 = new SequenceStep("c2", SequenceStepType.CONVERSATION, "Conversation 2",
                new ConversationStepConfig(11L, ConversationStepConfig.DEFAULT_ATTEMPTS), null, null, null, null, null);
        campaign = Campaign.builder()
                .id(CAMPAIGN_ID)
                .name("c")
                .status(CampaignStatus.ACTIVE)
                .companyId(COMPANY_ID)
                .schedule(new CampaignSchedule(null, null, LocalTime.of(9, 0), LocalTime.of(20, 0),
                        EnumSet.allOf(DayOfWeek.class)))
                .sequence(new CampaignSequence("c1", List.of(c1, text, wait, c2), SequenceOutcome.createDefaults()))
                .build();
        when(campaigns.find(COMPANY_ID, CAMPAIGN_ID)).thenReturn(campaign);
    }

    private void givenTargetOnStep(String stepId, int stepAttempts) {
        when(targets.find(COMPANY_ID, TARGET_ID)).thenReturn(new CampaignTarget(TARGET_ID, CAMPAIGN_ID, 1L, PHONE,
                "uz-UZ", "{}", TargetStatus.IN_PROGRESS, stepAttempts, false, null, null, null, stepId, stepAttempts));
    }

    private void givenClassifierPicks(String outcomeId) {
        when(classifier.findMatchingOutcomeId(eq(COMPANY_ID), eq(TRANSCRIPT), anyList())).thenReturn(outcomeId);
    }

    @Test
    void anUnansweredCallWaitsTheNextAttemptsOwnDelayWithoutAskingTheClassifier() {
        givenTargetOnStep("c1", 1);
        Instant expected = Instant.now().plus(Duration.ofDays(3));

        service.applyOutcome(COMPANY_ID, TARGET_ID, Disposition.NO_ANSWER, null);

        // Attempt 2 is set 3 days after attempt 1 — not the campaign's standard no-answer delay.
        verify(targets).updateStatus(eq(TARGET_ID), eq(TargetStatus.PENDING), argThat(next ->
                Duration.between(expected, next).abs().compareTo(Duration.ofMinutes(1)) < 0));
        verify(targets, never()).moveToStep(anyLong(), any(), any());
        verifyNoInteractions(classifier);
    }

    @Test
    void spentAttemptsFollowNoResponseThroughTheSmsAndTheWaitToTheNextConversation() {
        givenTargetOnStep("c1", 2);

        service.applyOutcome(COMPANY_ID, TARGET_ID, Disposition.NO_ANSWER, null);

        verify(targets).recordOutcome(TARGET_ID, CampaignOutcomeService.NO_RESPONSE, false);
        verify(sms).sendSms(COMPANY_ID, new SmsSendRequest(PHONE, "Salom"));
        verify(targets).moveToStep(eq(TARGET_ID), eq("c2"), isNotNull());
    }

    @Test
    void aGlobalSuccessEndsTheSequenceAsAConversion() {
        givenTargetOnStep("c1", 1);
        givenClassifierPicks("success");

        service.applyOutcome(COMPANY_ID, TARGET_ID, Disposition.COMPLETED, TRANSCRIPT);

        verify(targets).recordOutcome(TARGET_ID, "Success", true);
        verify(targets).updateStatus(TARGET_ID, TargetStatus.DONE, null);
    }

    @Test
    void theStepsOwnOutcomeTagsTheContactAndMovesOnAfterItsDelay() {
        givenTargetOnStep("c1", 1);
        givenClassifierPicks("callback");

        service.applyOutcome(COMPANY_ID, TARGET_ID, Disposition.COMPLETED, TRANSCRIPT);

        verify(contacts).addTag(COMPANY_ID, PHONE, "callback");
        verify(targets).recordOutcome(TARGET_ID, "Callback", false);
        verify(targets).moveToStep(eq(TARGET_ID), eq("c2"), isNotNull());
    }

    @Test
    void doNotCallPutsTheNumberOnTheList() {
        givenTargetOnStep("c1", 1);
        givenClassifierPicks("do_not_call");

        service.applyOutcome(COMPANY_ID, TARGET_ID, Disposition.COMPLETED, TRANSCRIPT);

        verify(doNotCall).add(eq(COMPANY_ID), eq(PHONE), anyString(), eq(DoNotCallSource.CALL));
        verify(targets).setDoNotCall(COMPANY_ID, TARGET_ID);
        verify(targets).updateStatus(TARGET_ID, TargetStatus.DONE, null);
    }

    @Test
    void anAnsweredCallNoOutcomeFitsFollowsOther() {
        givenTargetOnStep("c1", 1);
        givenClassifierPicks(null);

        service.applyOutcome(COMPANY_ID, TARGET_ID, Disposition.COMPLETED, TRANSCRIPT);

        verify(targets).recordOutcome(TARGET_ID, CampaignOutcomeService.OTHER, false);
        verify(targets).updateStatus(TARGET_ID, TargetStatus.DONE, null);
    }

    private void givenAutomaticRetries() {
        when(campaigns.find(COMPANY_ID, CAMPAIGN_ID)).thenReturn(campaign.toBuilder()
                .autoRetries(CampaignAutoRetries.DEFAULT).build());
    }

    @Test
    void doubleDialRedialsAnUnansweredCallOnceWithinTheSameAttempt() {
        givenAutomaticRetries();
        givenTargetOnStep("c1", 1);
        when(targets.redialOnce(TARGET_ID)).thenReturn(true);

        service.applyOutcome(COMPANY_ID, TARGET_ID, Disposition.NO_ANSWER, null);

        verify(targets).redialOnce(TARGET_ID);
        verify(targets, never()).updateStatus(anyLong(), any(), any());
        verify(sms, never()).sendSms(anyLong(), any());
    }

    @Test
    void theSecondDialOfAnAttemptFallsBackToTheNormalRetry() {
        givenAutomaticRetries();
        givenTargetOnStep("c1", 1);
        when(targets.redialOnce(TARGET_ID)).thenReturn(false);

        service.applyOutcome(COMPANY_ID, TARGET_ID, Disposition.NO_ANSWER, null);

        verify(targets).updateStatus(eq(TARGET_ID), eq(TargetStatus.PENDING), isNotNull());
    }

    @Test
    void aVoicemailIsNeverDoubleDialled() {
        givenAutomaticRetries();
        givenTargetOnStep("c1", 1);

        service.applyOutcome(COMPANY_ID, TARGET_ID, Disposition.VOICEMAIL, null);

        verify(targets, never()).redialOnce(anyLong());
    }

    @Test
    void anAutoScheduledCallbackWithNoTimeGoesADayLaterAndRestartsTheStep() {
        givenAutomaticRetries();
        givenTargetOnStep("c1", 2);
        Instant dayLater = Instant.now().plus(Duration.ofHours(24)).minus(Duration.ofMinutes(1));

        service.applyOutcome(COMPANY_ID, TARGET_ID, Disposition.CALLBACK_REQUESTED, TRANSCRIPT);

        // At least a day on; the calling window (09–20) may push it to the next opening.
        verify(targets).moveToStep(eq(TARGET_ID), eq("c1"), argThat(at ->
                at.isAfter(dayLater) && at.isBefore(dayLater.plus(Duration.ofHours(14)))));
        verifyNoInteractions(classifier);
    }

    @Test
    void withCallbacksOffACallbackRequestIsJudgedByTheOutcomes() {
        givenTargetOnStep("c1", 1);
        givenClassifierPicks("callback");

        service.applyOutcome(COMPANY_ID, TARGET_ID, Disposition.CALLBACK_REQUESTED, TRANSCRIPT);

        verify(targets).recordOutcome(TARGET_ID, "Callback", false);
    }

    @Test
    void aTargetWithNoStepYetStartsOnTheStartStep() {
        givenTargetOnStep(null, 2);

        service.applyOutcome(COMPANY_ID, TARGET_ID, Disposition.NO_ANSWER, null);

        verify(targets).moveToStep(eq(TARGET_ID), eq("c2"), isNotNull());
    }
}
