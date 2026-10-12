package uz.murodjon.robotcallv2.promise.application.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.conversion.application.port.input.ConversionUseCase;
import uz.murodjon.robotcallv2.promise.application.port.output.PaymentPromiseRepository;
import uz.murodjon.robotcallv2.promise.application.port.output.PaymentPromiseSettingsRepository;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromise;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromiseSettings;
import uz.murodjon.robotcallv2.promise.domain.enums.PaymentPromiseStatus;
import uz.murodjon.robotcallv2.sms.application.dto.SmsSendRequest;
import uz.murodjon.robotcallv2.sms.application.port.input.SmsUseCase;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentPromiseSchedulerTest {

    /** 10:00 in UTC — the company zone falls back to the clock's, so this is daytime there. */
    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");
    private static final long COMPANY_ID = 1L;
    private static final long TARGET_ID = 20L;
    private static final String PHONE = "+998901234567";

    private final PaymentPromiseRepository repository = mock(PaymentPromiseRepository.class);
    private final PaymentPromiseSettingsRepository settingsRepository = mock(PaymentPromiseSettingsRepository.class);
    private final CampaignTargetUseCase campaignTargetUseCase = mock(CampaignTargetUseCase.class);
    private final ConversionUseCase conversionUseCase = mock(ConversionUseCase.class);
    private final SmsUseCase smsUseCase = mock(SmsUseCase.class);
    private final PaymentPromiseScheduler scheduler = new PaymentPromiseScheduler(repository,
            new PaymentPromiseFollowUp(repository, settingsRepository, campaignTargetUseCase),
            conversionUseCase, smsUseCase, mock(CompanyConfigUseCase.class),
            (name, job) -> {
                job.run();
                return true;
            },
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void aReportedPaymentKeepsThePromiseAndSendsNothing() {
        open(promise(LocalDate.of(2026, 10, 15)), settings(true, true));
        when(conversionUseCase.existsEventByPhoneSince(eq(COMPANY_ID), eq(PHONE), eq("payment"), any()))
                .thenReturn(true);
        when(repository.updateStatusIfOpen(5L, PaymentPromiseStatus.KEPT, NOW)).thenReturn(true);

        scheduler.followUpAll();

        verify(repository).updateStatusIfOpen(5L, PaymentPromiseStatus.KEPT, NOW);
        verify(smsUseCase, never()).sendSms(anyLong(), any());
    }

    @Test
    void theDayBeforeTheDateTheCallerIsRemindedOnce() {
        open(promise(LocalDate.of(2026, 10, 15)), settings(true, false));
        when(smsUseCase.isEnabled()).thenReturn(true);
        when(smsUseCase.sendSms(eq(COMPANY_ID), any())).thenReturn(true);

        scheduler.followUpAll();

        verify(smsUseCase).sendSms(COMPANY_ID, new SmsSendRequest(PHONE, "15.10.2026 gacha to'lang"));
        verify(repository).markReminded(5L, NOW);
    }

    @Test
    void aMissedDatePastItsGraceBreaksThePromiseAndCallsTheTargetBack() {
        open(promise(LocalDate.of(2026, 10, 12)), settings(false, true));
        when(repository.updateStatusIfOpen(5L, PaymentPromiseStatus.BROKEN, NOW)).thenReturn(true);

        scheduler.followUpAll();

        verify(campaignTargetUseCase).scheduleCallback(COMPANY_ID, TARGET_ID, NOW);
    }

    @Test
    void withoutRecallABrokenPromiseIsOnlyMarked() {
        open(promise(LocalDate.of(2026, 10, 12)), settings(false, false));
        when(repository.updateStatusIfOpen(5L, PaymentPromiseStatus.BROKEN, NOW)).thenReturn(true);

        scheduler.followUpAll();

        verify(repository).updateStatusIfOpen(5L, PaymentPromiseStatus.BROKEN, NOW);
        verify(campaignTargetUseCase, never()).scheduleCallback(anyLong(), anyLong(), any());
    }

    private void open(PaymentPromise promise, PaymentPromiseSettings settings) {
        when(repository.findOpenAfterId(eq(0L), anyInt())).thenReturn(List.of(promise));
        when(settingsRepository.findByCompanyId(COMPANY_ID)).thenReturn(settings);
    }

    private static PaymentPromise promise(LocalDate promisedDate) {
        return new PaymentPromise(5L, COMPANY_ID, 10L, TARGET_ID, PHONE, promisedDate, null,
                PaymentPromiseStatus.OPEN, null, null, Instant.parse("2026-10-09T10:00:00Z"));
    }

    /** One day of reminder lead, one grace day, payments under the "payment" goal. */
    private static PaymentPromiseSettings settings(boolean reminderEnabled, boolean recallOnBroken) {
        return new PaymentPromiseSettings(COMPANY_ID, reminderEnabled, 1, "{date} gacha to'lang", 1,
                recallOnBroken, "payment");
    }
}
