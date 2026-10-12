package uz.murodjon.robotcallv2.alert.application.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.alert.application.port.output.AlertMetricRepository;
import uz.murodjon.robotcallv2.alert.application.port.output.AlertRuleRepository;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertMetricValue;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertRule;
import uz.murodjon.robotcallv2.alert.domain.enums.AlertComparator;
import uz.murodjon.robotcallv2.alert.domain.enums.AlertMetric;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AlertRuleSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");
    private static final long COMPANY_ID = 1L;

    private final AlertRuleRepository repository = mock(AlertRuleRepository.class);
    private final AlertMetricRepository metricRepository = mock(AlertMetricRepository.class);
    private final NotificationUseCase notificationUseCase = mock(NotificationUseCase.class);
    private final AlertRuleScheduler scheduler = new AlertRuleScheduler(repository,
            new AlertMetricReader(metricRepository), notificationUseCase,
            (name, job) -> {
                job.run();
                return true;
            },
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void aBreachedRuleNotifiesItsCompanyAndRecordsTheFiring() {
        when(repository.findEnabled()).thenReturn(List.of(transferRule(5L, null)));
        when(metricRepository.findDispositionRate(eq(COMPANY_ID), isNull(), any(), any()))
                .thenReturn(new AlertMetricValue(35.0, 40));

        scheduler.evaluateAll();

        verify(notificationUseCase).notify(eq(COMPANY_ID), eq(NotificationType.ALERT_TRIGGERED),
                eq("Transfer"), anyString(), isNull());
        verify(repository).recordEvaluation(5L, 35.0, NOW, true);
    }

    @Test
    void aRuleInItsCooldownRecordsTheValueButSendsNothing() {
        when(repository.findEnabled()).thenReturn(List.of(transferRule(5L, NOW.minusSeconds(600))));
        when(metricRepository.findDispositionRate(eq(COMPANY_ID), isNull(), any(), any()))
                .thenReturn(new AlertMetricValue(35.0, 40));

        scheduler.evaluateAll();

        verify(notificationUseCase, never()).notify(anyLong(), any(), anyString(), anyString(), any());
        verify(repository).recordEvaluation(5L, 35.0, NOW, false);
    }

    @Test
    void oneRuleFailingDoesNotStopTheNext() {
        when(repository.findEnabled()).thenReturn(List.of(transferRule(5L, null), transferRule(6L, null)));
        when(metricRepository.findDispositionRate(eq(COMPANY_ID), isNull(), any(), any()))
                .thenThrow(new RuntimeException("db down"))
                .thenReturn(new AlertMetricValue(10.0, 40));

        scheduler.evaluateAll();

        verify(repository, never()).recordEvaluation(eq(5L), any(), any(), anyBoolean());
        verify(repository).recordEvaluation(6L, 10.0, NOW, false);
    }

    /** Transfers above 30% over the last hour, cooldown 60 minutes. */
    private static AlertRule transferRule(long id, Instant lastTriggeredAt) {
        return new AlertRule(id, COMPANY_ID, "Transfer", AlertMetric.TRANSFER_RATE, AlertComparator.ABOVE, 30,
                60, 10, 60, null, true, null, null, lastTriggeredAt, NOW);
    }
}
