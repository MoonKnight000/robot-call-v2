package uz.murodjon.robotcallv2.agent.alerting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallAttemptJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.config.CompanyProperties;
import uz.murodjon.robotcallv2.notification.application.service.NotificationService;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AlertingServiceTest {

    private CallAttemptJpaRepository callAttempts;
    private NotificationService notificationService;
    private CompanyProperties companyProps;
    private VoiceMetrics metrics;
    private AlertingService alertingService;

    @BeforeEach
    void setUp() {
        callAttempts = mock(CallAttemptJpaRepository.class);
        notificationService = mock(NotificationService.class);
        companyProps = new CompanyProperties();
        companyProps.setDefaultId(1L);
        metrics = mock(VoiceMetrics.class);

        alertingService = new AlertingService(
                callAttempts,
                notificationService,
                companyProps,
                metrics,
                true, // enabled
                30,   // windowMinutes
                20,   // minSample
                0.3,  // threshold (30%)
                1000, // turnaroundBudgetMs
                30    // turnaroundMinTurns
        );
    }

    @Test
    void checkSuccessRateDoesNothingWhenDisabled() {
        AlertingService disabledService = new AlertingService(
                callAttempts, notificationService, companyProps, metrics,
                false, 30, 20, 0.3, 1000, 30);

        disabledService.checkSuccessRate();

        verifyNoInteractions(callAttempts);
        verifyNoInteractions(notificationService);
    }

    @Test
    void checkSuccessRateIgnoresWhenSampleIsBelowMinimum() {
        when(callAttempts.countByEndedAtGreaterThanEqual(any(Instant.class))).thenReturn(15L); // < 20

        alertingService.checkSuccessRate();

        verify(notificationService, never()).notify(anyLong(), any(), any(), any(), any());
    }

    @Test
    void checkSuccessRateTriggersAlertWhenRateIsBelowThreshold() {
        when(callAttempts.countByEndedAtGreaterThanEqual(any(Instant.class))).thenReturn(100L);
        // Only 10 promises out of 100 calls = 10% (< 30% threshold)
        when(callAttempts.countByEndedAtGreaterThanEqualAndDisposition(any(Instant.class), eq(Disposition.PROMISE_TO_PAY)))
                .thenReturn(10L);

        alertingService.checkSuccessRate();

        verify(notificationService).notify(
                eq(1L),
                eq(NotificationType.ERROR_OCCURRED),
                eq("Xato yuz berdi"),
                contains("10%"),
                isNull()
        );
    }

    @Test
    void checkSuccessRateDoesNotAlertWhenAboveThreshold() {
        when(callAttempts.countByEndedAtGreaterThanEqual(any(Instant.class))).thenReturn(100L);
        when(callAttempts.countByEndedAtGreaterThanEqualAndDisposition(any(Instant.class), eq(Disposition.PROMISE_TO_PAY)))
                .thenReturn(45L); // 45% >= 30%

        alertingService.checkSuccessRate();

        verify(notificationService, never()).notify(anyLong(), any(), any(), any(), any());
    }

    @Test
    void checkTurnaroundAlertsWhenP95ExceedsBudget() {
        when(metrics.turnaroundCount()).thenReturn(50L);
        when(metrics.turnaroundP95Millis()).thenReturn(1500.0); // 1500ms > 1000ms budget

        alertingService.checkTurnaround();

        verify(notificationService).notify(
                eq(1L),
                eq(NotificationType.ERROR_OCCURRED),
                eq("Javob kechikmoqda"),
                contains("1500 ms"),
                isNull()
        );
    }
}
