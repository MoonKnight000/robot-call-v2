package uz.murodjon.robotcallv2.campaign.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignTargetRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignRecurrence;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignRetry;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignSchedule;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.RetryPolicy;
import uz.murodjon.robotcallv2.campaign.domain.entity.RetryRule;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;
import uz.murodjon.robotcallv2.campaign.infrastructure.config.RetryProperties;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.sms.application.dto.SmsSendRequest;
import uz.murodjon.robotcallv2.sms.application.port.input.SmsUseCase;

import java.time.*;
import java.util.EnumSet;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The follow-up SMS a campaign's rule sends after an outcome — the second channel of a cadence. */
class CampaignOutcomeServiceTest {

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
    }

    private void givenCampaign(Map<Disposition, RetryRule> rules) {
        when(campaigns.find(COMPANY_ID, CAMPAIGN_ID)).thenReturn(Campaign.builder()
                .id(CAMPAIGN_ID)
                .name("c")
                .status(CampaignStatus.ACTIVE)
                .schedule(new CampaignSchedule(null, null, LocalTime.of(9, 0), LocalTime.of(20, 0),
                        EnumSet.allOf(DayOfWeek.class)))
                .retry(new CampaignRetry(3, 0, new RetryPolicy(rules)))
                .companyId(COMPANY_ID)
                .recurrence(new CampaignRecurrence(RecurrenceType.ONCE, null, null, false))
                .build());
    }

    private void givenTarget(int attempts, boolean doNotCall) {
        when(targets.find(COMPANY_ID, TARGET_ID)).thenReturn(new CampaignTarget(TARGET_ID, CAMPAIGN_ID, 1L, PHONE,
                "uz-UZ", "{\"clientName\": \"Aziza opa\"}", TargetStatus.IN_PROGRESS, attempts, doNotCall));
    }

    @Test
    void anUnansweredFirstCallIsTextedWithTheTargetsFacts() {
        givenCampaign(Map.of(Disposition.NO_ANSWER,
                new RetryRule(null, 180, "{{clientName | Hurmatli mijoz}}, sizga qo'ng'iroq qildik.", 1)));
        givenTarget(1, false);

        service.applyOutcome(COMPANY_ID, TARGET_ID, Disposition.NO_ANSWER);

        verify(sms).sendSms(COMPANY_ID, new SmsSendRequest(PHONE, "Aziza opa, sizga qo'ng'iroq qildik."));
    }

    @Test
    void aRetriedNumberIsTextedOnlyOnTheAttemptTheRuleNames() {
        givenCampaign(Map.of(Disposition.NO_ANSWER, new RetryRule(null, 180, "Salom", 1)));
        givenTarget(2, false);

        service.applyOutcome(COMPANY_ID, TARGET_ID, Disposition.NO_ANSWER);

        verify(sms, never()).sendSms(anyLong(), any());
    }

    @Test
    void aTerminalOutcomeCanBeConfirmedBySms() {
        givenCampaign(Map.of(Disposition.PROMISE_TO_PAY, new RetryRule(null, null, "Rahmat!", null)));
        givenTarget(1, false);

        service.applyOutcome(COMPANY_ID, TARGET_ID, Disposition.PROMISE_TO_PAY);

        verify(sms).sendSms(COMPANY_ID, new SmsSendRequest(PHONE, "Rahmat!"));
    }

    @Test
    void aDoNotCallTargetIsNeverTexted() {
        givenCampaign(Map.of(Disposition.NO_ANSWER, new RetryRule(null, 180, "Salom", null)));
        givenTarget(1, true);

        service.applyOutcome(COMPANY_ID, TARGET_ID, Disposition.NO_ANSWER);

        verify(sms, never()).sendSms(anyLong(), any());
    }

    @Test
    void aFailingGatewayStillReschedulesTheTarget() {
        givenCampaign(Map.of(Disposition.NO_ANSWER, new RetryRule(null, 180, "Salom", null)));
        givenTarget(1, false);
        when(sms.sendSms(anyLong(), any())).thenThrow(new IllegalStateException("gateway down"));

        service.applyOutcome(COMPANY_ID, TARGET_ID, Disposition.NO_ANSWER);

        verify(targets).updateStatus(eq(TARGET_ID), eq(TargetStatus.PENDING), any(Instant.class));
    }
}
