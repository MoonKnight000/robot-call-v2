package uz.murodjon.robotcallv2.agent.alerting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.metrics.DialogMetrics;
import uz.murodjon.robotcallv2.agent.metrics.LlmMetrics;
import uz.murodjon.robotcallv2.agent.metrics.RealtimeMetrics;
import uz.murodjon.robotcallv2.agent.metrics.RtpMetrics;
import uz.murodjon.robotcallv2.agent.metrics.SttMetrics;
import uz.murodjon.robotcallv2.agent.metrics.TtsMetrics;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.company.infrastructure.config.CompanyProperties;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AlertingServiceTest {

    private CallAttemptUseCase callAttemptUseCase;
    private NotificationUseCase notificationService;
    private CompanyProperties companyProperties;
    private SttMetrics sttMetrics;
    private RealtimeMetrics realtimeMetrics;
    private RtpMetrics rtpMetrics;
    private AlertingProperties alertingProperties;
    private AlertingService alertingService;

    @BeforeEach
    void setUp() {
        callAttemptUseCase = mock(CallAttemptUseCase.class);
        notificationService = mock(NotificationUseCase.class);
        companyProperties = new CompanyProperties();
        companyProperties.setDefaultId(1L);
        sttMetrics = mock(SttMetrics.class);
        realtimeMetrics = mock(RealtimeMetrics.class);
        rtpMetrics = mock(RtpMetrics.class);

        alertingProperties = new AlertingProperties(
                true, // enabled
                30,   // windowMinutes
                20,   // minSample
                0.3,  // successThreshold (30%)
                5,    // checkMinutes
                1000, // turnaroundBudgetMs
                30,   // turnaroundMinTurns
                0.4,  // speculationMinHitRate
                5,    // providerFailoverThreshold
                3.5   // minMeanMos
        );

        alertingService = createAlertingService(alertingProperties);
    }

    private AlertingService createAlertingService(AlertingProperties properties) {
        return new AlertingService(callAttemptUseCase, notificationService, companyProperties,
                mock(DialogMetrics.class), mock(LlmMetrics.class), sttMetrics, mock(TtsMetrics.class),
                realtimeMetrics, rtpMetrics, properties, AlertingServiceTest::runHere);
    }

    private static boolean runHere(String jobName, Runnable job) {
        job.run();
        return true;
    }

    @Test
    void checkSuccessRateDoesNothingWhenDisabled() {
        AlertingProperties disabledProperties = new AlertingProperties(
                false, 30, 20, 0.3, 5, 1000, 30, 0.4, 5, 3.5);
        AlertingService disabledService = createAlertingService(disabledProperties);

        disabledService.checkSuccessRate();

        verifyNoInteractions(callAttemptUseCase);
        verifyNoInteractions(notificationService);
    }

    @Test
    void checkSuccessRateSkipsWhenSampleTooSmall() {
        when(callAttemptUseCase.countEndedSince(any(Instant.class))).thenReturn(10L);

        alertingService.checkSuccessRate();

        verify(callAttemptUseCase).countEndedSince(any(Instant.class));
        verifyNoMoreInteractions(callAttemptUseCase);
        verifyNoInteractions(notificationService);
    }

    @Test
    void checkSuccessRateSkipsWhenRateAboveThreshold() {
        when(callAttemptUseCase.countEndedSince(any(Instant.class))).thenReturn(100L);
        when(callAttemptUseCase.countEndedSinceWithDisposition(any(Instant.class), eq(Disposition.PROMISE_TO_PAY))).thenReturn(80L);

        alertingService.checkSuccessRate();

        verifyNoInteractions(notificationService);
    }

    @Test
    void checkSuccessRateFiresAlertWhenRateBelowThreshold() {
        when(callAttemptUseCase.countEndedSince(any(Instant.class))).thenReturn(100L);
        // Only 20 answered out of 100 -> 20% < 30% threshold
        when(callAttemptUseCase.countEndedSinceWithDisposition(any(Instant.class), eq(Disposition.PROMISE_TO_PAY))).thenReturn(20L);

        alertingService.checkSuccessRate();

        verify(notificationService).notify(
                eq(1L),
                eq(NotificationType.ERROR_OCCURRED),
                anyString(),
                anyString(),
                isNull()
        );
    }

    @Test
    void checkSuccessRateCountsACompletedScenarioAsSuccess() {
        when(callAttemptUseCase.countEndedSince(any(Instant.class))).thenReturn(100L);
        // 10 promises + 30 completed = 40%, above the 30% threshold; the promises alone would alert.
        when(callAttemptUseCase.countEndedSinceWithDisposition(any(Instant.class), eq(Disposition.PROMISE_TO_PAY))).thenReturn(10L);
        when(callAttemptUseCase.countEndedSinceWithDisposition(any(Instant.class), eq(Disposition.COMPLETED))).thenReturn(30L);

        alertingService.checkSuccessRate();

        verifyNoInteractions(notificationService);
    }

    @Test
    void providerHealthAlertsOnABurstOfFailovers() {
        when(rtpMetrics.mosTotals()).thenReturn(new double[]{0, 0});
        alertingService.checkProviderHealth(); // baseline

        when(sttMetrics.countFailovers()).thenReturn(4.0);
        when(realtimeMetrics.countFallbacks()).thenReturn(3.0);
        alertingService.checkProviderHealth();

        verify(notificationService).notify(eq(1L), eq(NotificationType.ERROR_OCCURRED), anyString(), anyString(), isNull());
    }

    @Test
    void providerHealthAlertsOnPoorAudio() {
        when(rtpMetrics.mosTotals()).thenReturn(new double[]{0, 0});
        alertingService.checkProviderHealth(); // baseline

        when(rtpMetrics.mosTotals()).thenReturn(new double[]{40, 40 * 3.1});
        alertingService.checkProviderHealth();

        verify(notificationService).notify(eq(1L), eq(NotificationType.ERROR_OCCURRED), anyString(), anyString(), isNull());
    }

    @Test
    void checkSuccessRateHandlesZeroTotalGracefully() {
        when(callAttemptUseCase.countEndedSince(any(Instant.class))).thenReturn(0L);

        alertingService.checkSuccessRate();

        verifyNoInteractions(notificationService);
    }
}
