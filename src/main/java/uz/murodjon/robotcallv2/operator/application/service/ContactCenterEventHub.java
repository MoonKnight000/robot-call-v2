package uz.murodjon.robotcallv2.operator.application.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.operator.application.port.input.ContactCenterEventUseCase;
import uz.murodjon.robotcallv2.operator.domain.enums.ContactCenterEventType;
import uz.murodjon.robotcallv2.shared.stream.EventSink;
import uz.murodjon.robotcallv2.shared.stream.EventStreamHub;
import uz.murodjon.robotcallv2.shared.stream.EventSubscription;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Push events for the operator panel and the queue wallboard. Unlike the general live
 * stream every subscription here is bound to one company — and a panel subscription to one
 * user — so an offer meant for one operator never reaches another, and no event ever
 * crosses a tenant.
 *
 * <p>Publishing only queues the event for each subscription's own writer
 * ({@link EventStreamHub}). Events are published from the call-queue dispatcher and ARI
 * threads, and one slow browser must not hold those up.
 */
@Service
public class ContactCenterEventHub implements ContactCenterEventUseCase {

    private static final long HEARTBEAT_MS = 15_000;

    /** {@code userId} null is a wallboard subscription; otherwise one operator's panel. */
    private record Audience(long companyId, Long userId) {

        private boolean isWallboard() {
            return userId == null;
        }
    }

    private final EventStreamHub<Audience> streams = new EventStreamHub<>("contact-center-sse");

    @Override
    public EventSubscription subscribeUser(long companyId, long userId, EventSink sink) {
        return streams.subscribe(new Audience(companyId, userId), sink);
    }

    @Override
    public EventSubscription subscribeWallboard(long companyId, EventSink sink) {
        return streams.subscribe(new Audience(companyId, null), sink);
    }

    @Override
    public void publishToUser(long userId, ContactCenterEventType type, Object payload) {
        streams.publish(audience -> audience.userId() != null && audience.userId() == userId, type.name(), payload);
    }

    @Override
    public void publishToCompany(long companyId, ContactCenterEventType type, Object payload) {
        streams.publish(audience -> audience.companyId() == companyId, type.name(), payload);
    }

    @Override
    public void publishToWallboard(long companyId, ContactCenterEventType type, Object payload) {
        streams.publish(audience -> audience.companyId() == companyId && audience.isWallboard(), type.name(), payload);
    }

    @Override
    public Set<Long> findWallboardCompanyIds() {
        return streams.findAudiences().stream()
                .filter(Audience::isWallboard)
                .map(Audience::companyId)
                .collect(Collectors.toSet());
    }

    /** Whether anyone is watching this company's wallboard — the stats tick is skipped otherwise. */
    public boolean hasWallboard(long companyId) {
        return streams.findAudiences().stream()
                .anyMatch(audience -> audience.companyId() == companyId && audience.isWallboard());
    }

    @Scheduled(fixedRate = HEARTBEAT_MS)
    void heartbeat() {
        streams.sendHeartbeat();
    }
}
