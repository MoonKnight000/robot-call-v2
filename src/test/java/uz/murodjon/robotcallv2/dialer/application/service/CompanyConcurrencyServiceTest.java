package uz.murodjon.robotcallv2.dialer.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;
import uz.murodjon.robotcallv2.dialer.application.dto.ConcurrencyResponse;
import uz.murodjon.robotcallv2.dialer.infrastructure.config.DialerProperties;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CompanyConcurrencyServiceTest {

    private static final long COMPANY_ID = 1L;
    /** Platform default per company: 5. */
    private static final DialerProperties PROPERTIES = new DialerProperties(true, 5, 10, 50, 5, 60, 0);

    private DialerState state;
    private CompanyUseCase companyUseCase;
    private NotificationUseCase notificationUseCase;
    private CompanyConcurrencyService service;

    @BeforeEach
    void setUp() {
        state = mock(DialerState.class);
        companyUseCase = mock(CompanyUseCase.class);
        notificationUseCase = mock(NotificationUseCase.class);
        service = new CompanyConcurrencyService(PROPERTIES, state, companyUseCase, notificationUseCase,
                Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"), ZoneOffset.UTC));
    }

    private void givenCompanyLimit(Integer limit) {
        when(companyUseCase.findById(COMPANY_ID)).thenReturn(
                new Company(COMPANY_ID, "c", CompanyStatus.ACTIVE, null, null, null, limit, null, null, null));
    }

    @Test
    void withoutItsOwnLimitTheCompanyGetsThePlatformDefault() {
        givenCompanyLimit(null);
        when(state.countActive(COMPANY_ID)).thenReturn(2);

        assertThat(service.findConcurrency(COMPANY_ID)).isEqualTo(new ConcurrencyResponse(5, true, 2, 3));
        assertThat(service.countFree(COMPANY_ID)).isEqualTo(3);
    }

    @Test
    void theCompanysOwnLimitReplacesTheDefault() {
        givenCompanyLimit(40);
        when(state.countActive(COMPANY_ID)).thenReturn(10);

        assertThat(service.findConcurrency(COMPANY_ID)).isEqualTo(new ConcurrencyResponse(40, false, 10, 30));
        assertThat(service.countFree(COMPANY_ID)).isEqualTo(30);
        verify(notificationUseCase, never()).notify(anyLong(), any(), anyString(), anyString(), any());
    }

    @Test
    void reachingTheLimitIsToldOnceAnHourNotOnceATick() {
        givenCompanyLimit(3);
        when(state.countActive(COMPANY_ID)).thenReturn(3);

        assertThat(service.countFree(COMPANY_ID)).isZero();
        service.countFree(COMPANY_ID);
        service.countFree(COMPANY_ID);

        verify(notificationUseCase, times(1)).notify(eq(COMPANY_ID), eq(NotificationType.CONCURRENCY_LIMIT_REACHED),
                anyString(), anyString(), any());
        assertThat(service.findConcurrency(COMPANY_ID).available()).isZero();
    }
}
