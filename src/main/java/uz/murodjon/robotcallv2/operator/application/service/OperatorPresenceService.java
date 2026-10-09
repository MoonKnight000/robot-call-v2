package uz.murodjon.robotcallv2.operator.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.operator.application.port.input.ContactCenterEventUseCase;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorPresenceUseCase;
import uz.murodjon.robotcallv2.operator.application.port.output.OperatorStatusLogRepository;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorPresence;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorStatusChange;
import uz.murodjon.robotcallv2.operator.domain.enums.ContactCenterEventType;
import uz.murodjon.robotcallv2.operator.domain.enums.OperatorStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Every operator's live presence (docs/INBOUND-PBX-PLAN.md §7.2).
 *
 * <p>Held in memory, because the queue asks it on every offer and a database round trip
 * per ring is the wrong price; every change is also written to operator_status_log, which
 * is what the "time in status" report reads and what a restart restores from. One
 * instance holds the truth — the deployment runs one voice-agent instance per Asterisk.
 *
 * <p>Transitions are compare-and-set on the map entry, so two queues offering calls at
 * once can never both ring the same operator: only one {@link #claimForRinging} wins.
 */
@Service
public class OperatorPresenceService implements OperatorPresenceUseCase {

    private static final Logger log = LoggerFactory.getLogger(OperatorPresenceService.class);

    /** Offers left ringing in a row before the operator is taken off the queue. */
    private static final int MAX_CONSECUTIVE_MISSES = 2;
    private static final String MISSED_REASON = "javob bermadi";

    private final Map<Long, OperatorPresence> presences = new ConcurrentHashMap<>();
    private final OperatorStatusLogRepository statusLogRepository;
    private final ContactCenterEventUseCase contactCenterEventUseCase;
    private final Clock clock;
    private volatile LocalDate countersDay;

    public OperatorPresenceService(OperatorStatusLogRepository statusLogRepository,
                                   ContactCenterEventUseCase contactCenterEventUseCase, Clock clock) {
        this.statusLogRepository = statusLogRepository;
        this.contactCenterEventUseCase = contactCenterEventUseCase;
        this.clock = clock;
        this.countersDay = LocalDate.now(clock);
    }

    @Override
    public OperatorPresence find(long companyId, long userId) {
        resetCountersOnNewDay();
        OperatorPresence presence = presences.get(userId);
        return presence != null ? presence : OperatorPresence.offline(userId, companyId, clock.instant());
    }

    public List<OperatorPresence> findByCompany(long companyId) {
        resetCountersOnNewDay();
        return presences.values().stream().filter(p -> p.companyId() == companyId).toList();
    }

    @Override
    public OperatorPresence setStatus(long companyId, long userId, OperatorStatus status, String reason) {
        Instant now = clock.instant();
        OperatorPresence updated = presences.compute(userId, (id, current) -> {
            OperatorPresence base = current != null ? current : OperatorPresence.offline(userId, companyId, now);
            return base.withStatus(status, status == OperatorStatus.BREAK ? reason : null, now, null);
        });
        changed(updated);
        return updated;
    }

    @Override
    public boolean claimForRinging(long userId, String channelId) {
        Instant now = clock.instant();
        AtomicReference<OperatorPresence> claimed = new AtomicReference<>();
        presences.computeIfPresent(userId, (id, current) -> {
            if (current.status() != OperatorStatus.AVAILABLE) {
                return current;
            }
            OperatorPresence ringing = current.withStatus(OperatorStatus.RINGING, null, now, channelId);
            claimed.set(ringing);
            return ringing;
        });
        if (claimed.get() == null) {
            return false;
        }
        changed(claimed.get());
        return true;
    }

    @Override
    public void releaseRinging(long userId) {
        transitionFrom(userId, OperatorStatus.RINGING,
                current -> current.withStatus(OperatorStatus.AVAILABLE, null, clock.instant(), null));
    }

    @Override
    public void markMissed(long userId) {
        Instant now = clock.instant();
        transitionFrom(userId, OperatorStatus.RINGING, current -> {
            int misses = current.consecutiveMisses() + 1;
            return misses >= MAX_CONSECUTIVE_MISSES
                    ? current.missed(misses, OperatorStatus.BREAK, MISSED_REASON, now)
                    : current.missed(misses, OperatorStatus.AVAILABLE, null, now);
        });
    }

    @Override
    public void markOnCall(long userId, String channelId) {
        Instant now = clock.instant();
        OperatorPresence updated = presences.computeIfPresent(userId, (id, current) -> current.answered(now, channelId));
        if (updated != null) {
            changed(updated);
        }
    }

    @Override
    public void markCallEnded(long userId, int wrapUpSec) {
        Instant now = clock.instant();
        OperatorPresence updated = presences.computeIfPresent(userId, (id, current) ->
                current.status() == OperatorStatus.ON_CALL
                        ? current.afterCall(now, now.plusSeconds(Math.max(0, wrapUpSec)))
                        : current);
        if (updated != null && updated.status() == OperatorStatus.WRAP_UP) {
            changed(updated);
        }
    }

    @Override
    public void finishWrapUp(long userId) {
        Instant now = clock.instant();
        transitionFrom(userId, OperatorStatus.WRAP_UP,
                current -> current.withStatus(OperatorStatus.AVAILABLE, null, now, null));
    }

    /** Ends every wrap-up whose time is up. */
    @Scheduled(fixedDelay = 1000)
    void releaseWrapUps() {
        Instant now = clock.instant();
        for (OperatorPresence presence : presences.values()) {
            if (presence.status() == OperatorStatus.WRAP_UP && presence.wrapUpUntil() != null
                    && !now.isBefore(presence.wrapUpUntil())) {
                transitionFrom(presence.userId(), OperatorStatus.WRAP_UP,
                        current -> current.withStatus(OperatorStatus.AVAILABLE, null, now, null));
            }
        }
    }

    /**
     * Brings back who was AVAILABLE or on BREAK before a restart. Anyone caught mid-call is
     * AVAILABLE again: the call they were on did not survive the restart either.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void restore() {
        try {
            Instant now = clock.instant();
            for (OperatorStatusChange open : statusLogRepository.findOpen()) {
                if (open.status() == OperatorStatus.OFFLINE) {
                    continue;
                }
                OperatorStatus status = open.status() == OperatorStatus.BREAK ? OperatorStatus.BREAK : OperatorStatus.AVAILABLE;
                OperatorPresence presence = OperatorPresence.offline(open.userId(), open.companyId(), now)
                        .withStatus(status, status == OperatorStatus.BREAK ? open.reason() : null,
                                status == open.status() ? open.startedAt() : now, null);
                presences.put(open.userId(), presence);
                if (status != open.status()) {
                    changed(presence);
                }
            }
            log.info("Restored presence of {} operator(s)", presences.size());
        } catch (Exception e) {
            log.warn("Could not restore operator presence: {}", e.getMessage());
        }
    }

    private void transitionFrom(long userId, OperatorStatus from,
                                java.util.function.UnaryOperator<OperatorPresence> change) {
        AtomicReference<OperatorPresence> changedTo = new AtomicReference<>();
        presences.computeIfPresent(userId, (id, current) -> {
            if (current.status() != from) {
                return current;
            }
            OperatorPresence next = change.apply(current);
            changedTo.set(next);
            return next;
        });
        if (changedTo.get() != null) {
            changed(changedTo.get());
        }
    }

    private void changed(OperatorPresence presence) {
        try {
            statusLogRepository.record(new OperatorStatusChange(presence.userId(), presence.companyId(),
                    presence.status(), presence.reason(), presence.since(), null));
        } catch (Exception e) {
            log.warn("Could not log status {} of operator {}: {}", presence.status(), presence.userId(), e.getMessage());
        }
        contactCenterEventUseCase.publishToUser(presence.userId(), ContactCenterEventType.STATUS_CHANGED, presence);
    }

    private void resetCountersOnNewDay() {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(countersDay)) {
            countersDay = today;
            presences.replaceAll((id, presence) -> presence.withCallsToday(0));
        }
    }
}
