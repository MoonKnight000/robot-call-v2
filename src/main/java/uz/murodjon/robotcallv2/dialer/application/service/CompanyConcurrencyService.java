package uz.murodjon.robotcallv2.dialer.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.billing.application.port.output.ChannelUsagePort;
import uz.murodjon.robotcallv2.billing.domain.entity.ChannelUsage;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.dialer.application.dto.ConcurrencyResponse;
import uz.murodjon.robotcallv2.dialer.application.port.input.ConcurrencyUseCase;
import uz.murodjon.robotcallv2.dialer.infrastructure.config.DialerProperties;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A company's share of the dialer's concurrent calls: the limit its plan sets
 * ({@code company.max_concurrent_calls}), or the platform default
 * ({@code voice-agent.dialer.max-concurrent-calls-per-company}) when none is set, measured
 * against the calls {@link DialerState} counts for it.
 */
@Service
public class CompanyConcurrencyService implements ConcurrencyUseCase, ChannelUsagePort {

    /** How often a company is told its limit is holding calls back — once, not once a tick. */
    private static final Duration LIMIT_NOTICE_INTERVAL = Duration.ofHours(1);

    private final DialerProperties dialerProperties;
    private final DialerState state;
    private final CompanyUseCase companyUseCase;
    private final NotificationUseCase notificationUseCase;
    private final Clock clock;

    /** Company id → when it was last told its limit is reached. */
    private final Map<Long, Instant> limitNoticeAt = new ConcurrentHashMap<>();

    public CompanyConcurrencyService(DialerProperties dialerProperties, DialerState state,
                                     CompanyUseCase companyUseCase, NotificationUseCase notificationUseCase,
                                     Clock clock) {
        this.dialerProperties = dialerProperties;
        this.state = state;
        this.companyUseCase = companyUseCase;
        this.notificationUseCase = notificationUseCase;
        this.clock = clock;
    }

    /**
     * Slots the company has free for new calls; zero or less when it is at its limit, in
     * which case it is told so, at most once an hour.
     */
    public int countFree(long companyId) {
        int limit = findLimit(companyId);
        int free = limit - state.countActive(companyId);
        if (free <= 0) {
            noticeLimitReached(companyId, limit);
        }
        return free;
    }

    @Override
    public ConcurrencyResponse findConcurrency(long companyId) {
        Integer own = findOwnLimit(companyId);
        int limit = own != null ? own : dialerProperties.maxConcurrentCallsPerCompany();
        int active = state.countActive(companyId);
        return new ConcurrencyResponse(limit, own == null, active, Math.max(0, limit - active));
    }

    @Override
    public ChannelUsage findChannelUsage(long companyId) {
        return new ChannelUsage(state.countActive(companyId), findLimit(companyId));
    }

    private int findLimit(long companyId) {
        Integer own = findOwnLimit(companyId);
        return own != null ? own : dialerProperties.maxConcurrentCallsPerCompany();
    }

    private Integer findOwnLimit(long companyId) {
        Company company = companyUseCase.findById(companyId);
        return company != null ? company.maxConcurrentCalls() : null;
    }

    private void noticeLimitReached(long companyId, int limit) {
        Instant now = clock.instant();
        Instant last = limitNoticeAt.get(companyId);
        if (last != null && last.plus(LIMIT_NOTICE_INTERVAL).isAfter(now)) {
            return;
        }
        limitNoticeAt.put(companyId, now);
        notificationUseCase.notify(companyId, NotificationType.CONCURRENCY_LIMIT_REACHED,
                "Parallel qo'ng'iroqlar limiti to'ldi",
                "Bir vaqtda " + limit + " ta qo'ng'iroq limiti band — yangi qo'ng'iroqlar slot bo'shaguncha kutmoqda",
                null);
    }
}
