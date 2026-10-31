package uz.murodjon.robotcallv2.campaign.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignTargetRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignSchedule;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignSequence;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.ConversationStepConfig;
import uz.murodjon.robotcallv2.campaign.domain.entity.SequenceStep;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.SequenceOutcome;
import uz.murodjon.robotcallv2.campaign.domain.enums.SequenceStepType;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;
import uz.murodjon.robotcallv2.campaign.infrastructure.config.RetryProperties;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.sms.application.dto.SmsSendRequest;
import uz.murodjon.robotcallv2.sms.application.port.input.SmsUseCase;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** What a finished call does to a target of a campaign that has a sequence. */
class CampaignOutcomeSequenceTest {

    private static final long COMPANY_ID = 1L;
    private static final long CAMPAIGN_ID = 3L;
    private static final long TARGET_ID = 100L;
    private static final String PHONE = "998901112233";

    private CampaignRepository campaigns;
    private CampaignTargetRepository targets;
    private SmsUseCase sms;
    private CampaignOutcomeService service;

    @BeforeEach
    void setUp() {
        campaigns = mock(CampaignRepository.class);
        targets = mock(CampaignTargetRepository.class);
        sms = mock(SmsUseCase.class);
        when(sms.isEnabled()).thenReturn(true);
        service = new CampaignOutcomeService(campaigns, targets, mock(CompanyConfigUseCase.class),
                new RetryProperties(180, 15, 1200, false), mock(AuditUseCase.class),
                mock(NotificationUseCase.class), sms, new ObjectMapper(), Clock.systemUTC());
        // c1 (2 attempts): no response -> text -> wait an hour -> c2; every other outcome ends the sequence.
        SequenceStep c1 = new SequenceStep("c1", SequenceStepType.CONVERSATION, "Conversation 1",
                new ConversationStepConfig(10L, 2, 0), null, null,
                Map.of(SequenceOutcome.NO_RESPONSE, "sms"), null, null);
        SequenceStep text = new SequenceStep("sms", SequenceStepType.SMS, "SMS", null, "Salom", null,
                Map.of(), "wait", null);
        SequenceStep wait = new SequenceStep("wait", SequenceStepType.WAIT, "Wait", null, null, 60,
                Map.of(), "c2", null);
        SequenceStep c2 = new SequenceStep("c2", SequenceStepType.CONVERSATION, "Conversation 2",
                new ConversationStepConfig(11L, 3, 0), null, null, Map.of(), null, null);
        when(campaigns.find(COMPANY_ID, CAMPAIGN_ID)).thenReturn(Campaign.builder()
                .id(CAMPAIGN_ID)
                .name("c")
                .status(CampaignStatus.ACTIVE)
                .companyId(COMPANY_ID)
                .schedule(new CampaignSchedule(null, null, LocalTime.of(9, 0), LocalTime.of(20, 0),
                        EnumSet.allOf(DayOfWeek.class)))
                .sequence(new CampaignSequence("c1", List.of(c1, text, wait, c2)))
                .build());
    }

    private void givenTargetOnStep(String stepId, int stepAttempts) {
        when(targets.find(COMPANY_ID, TARGET_ID)).thenReturn(new CampaignTarget(TARGET_ID, CAMPAIGN_ID, 1L, PHONE,
                "uz-UZ", "{}", TargetStatus.IN_PROGRESS, stepAttempts, false, null, null, null, stepId, stepAttempts));
    }

    @Test
    void anUnansweredCallWithAttemptsLeftRetriesTheSameStep() {
        givenTargetOnStep("c1", 1);

        service.applyOutcome(COMPANY_ID, TARGET_ID, Disposition.NO_ANSWER);

        verify(targets).updateStatus(eq(TARGET_ID), eq(TargetStatus.PENDING), isNotNull());
        verify(targets, never()).moveToStep(anyLong(), any(), any());
    }

    @Test
    void spentAttemptsRouteNoResponseThroughTheSmsAndTheWaitToTheNextConversation() {
        givenTargetOnStep("c1", 2);

        service.applyOutcome(COMPANY_ID, TARGET_ID, Disposition.NO_ANSWER);

        verify(sms).sendSms(COMPANY_ID, new SmsSendRequest(PHONE, "Salom"));
        verify(targets).moveToStep(eq(TARGET_ID), eq("c2"), isNotNull());
    }

    @Test
    void anOutcomeWithoutARouteEndsTheTarget() {
        givenTargetOnStep("c1", 1);

        service.applyOutcome(COMPANY_ID, TARGET_ID, Disposition.PROMISE_TO_PAY);

        verify(targets).updateStatus(TARGET_ID, TargetStatus.DONE, null);
        verify(targets, never()).moveToStep(anyLong(), any(), any());
    }

    @Test
    void aTargetWithNoStepYetStartsOnTheStartStep() {
        givenTargetOnStep(null, 2);

        service.applyOutcome(COMPANY_ID, TARGET_ID, Disposition.NO_ANSWER);

        verify(targets).moveToStep(eq(TARGET_ID), eq("c2"), isNotNull());
    }
}
