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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AlertingServiceTest {

    private CallAttemptJpaRepository callAttemptJpaRepository;
    private NotificationService notificationService;
    private CompanyProperties companyProperties;
    private VoiceMetrics voiceMetrics;
    private AlertingProperties alertingProperties;
    private AlertingService alertingService;

    @BeforeEach
    void setUp() {
        callAttemptJpaRepository = mock(CallAttemptJpaRepository.class);
        notificationService = mock(NotificationService.class);
        companyProperties = new CompanyProperties();
        companyProperties.setDefaultId(1L);
        voiceMetrics = mock(VoiceMetrics.class);

        alertingProperties = new AlertingProperties(
                true, // enabled
                30,   // windowMinutes
                20,   // minSample
                0.3,  // successThreshold (30%)
                5,    // checkMinutes
                1000, // turnaroundBudgetMs
                30,   // turnaroundMinTurns
                0.4   // speculationMinHitRate
        );

        alertingService = new AlertingService(
                callAttemptJpaRepository,
                notificationService,
                companyProperties,
                voiceMetrics,
                alertingProperties
        );
    }

    @Test
    void checkSuccessRateDoesNothingWhenDisabled() {
        AlertingProperties disabledProperties = new AlertingProperties(
                false, 30, 20, 0.3, 5, 1000, 30, 0.4);
        AlertingService disabledService = new AlertingService(
                callAttemptJpaRepository, notificationService, companyProperties, voiceMetrics, disabledProperties);

        disabledService.checkSuccessRate();

        verifyNoInteractions(callAttemptJpaRepository);
        verifyNoInteractions(notificationService);
    }

    @Test
    void checkSuccessRateSkipsWhenSampleTooSmall() {
        when(callAttemptJpaRepository.countByEndedAtGreaterThanEqual(any(Instant.class))).thenReturn(10L);

        alertingService.checkSuccessRate();

        verify(callAttemptJpaRepository).countByEndedAtGreaterThanEqual(any(Instant.class));
        verifyNoMoreInteractions(callAttemptJpaRepository);
        verifyNoInteractions(notificationService);
    }

    @Test
    void checkSuccessRateSkipsWhenRateAboveThreshold() {
        when(callAttemptJpaRepository.countByEndedAtGreaterThanEqual(any(Instant.class))).thenReturn(100L);
        when(callAttemptJpaRepository.countByEndedAtGreaterThanEqualAndDisposition(any(Instant.class), eq(Disposition.PROMISE_TO_PAY))).thenReturn(80L);

        alertingService.checkSuccessRate();

        verifyNoInteractions(notificationService);
    }

    @Test
    void checkSuccessRateFiresAlertWhenRateBelowThreshold() {
        when(callAttemptJpaRepository.countByEndedAtGreaterThanEqual(any(Instant.class))).thenReturn(100L);
        // Only 20 answered out of 100 -> 20% < 30% threshold
        when(callAttemptJpaRepository.countByEndedAtGreaterThanEqualAndDisposition(any(Instant.class), eq(Disposition.PROMISE_TO_PAY))).thenReturn(20L);

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
    void checkSuccessRateHandlesZeroTotalGracefully() {
        when(callAttemptJpaRepository.countByEndedAtGreaterThanEqual(any(Instant.class))).thenReturn(0L);

        alertingService.checkSuccessRate();

        verifyNoInteractions(notificationService);
    }
}
