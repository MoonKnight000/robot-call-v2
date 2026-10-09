package uz.murodjon.robotcallv2.live.application.service;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.live.application.port.input.LiveUseCase;
import uz.murodjon.robotcallv2.live.application.port.output.LiveEventRelayPort;
import uz.murodjon.robotcallv2.live.domain.entity.LiveNotification;
import uz.murodjon.robotcallv2.live.domain.entity.LiveRelayEvent;
import uz.murodjon.robotcallv2.live.domain.enums.LiveEventType;
import uz.murodjon.robotcallv2.operator.domain.event.OperatorNotificationEvent;
import uz.murodjon.robotcallv2.shared.stream.EventSink;
import uz.murodjon.robotcallv2.shared.stream.EventStreamHub;
import uz.murodjon.robotcallv2.shared.stream.EventSubscription;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Fans out live events (§0.7) to the connected {@code GET /api/live/stream} clients of one
 * company. Every subscription is bound to the company of the token that opened it, and every
 * event names the company it belongs to — a transcript line or KPI tick never crosses a tenant.
 *
 * <p>{@link #publish} is called from media threads — the RTP consumer (audio levels), STT
 * and turn threads — so it only ever queues the event for the stream's own writer
 * ({@link EventStreamHub}): one supervisor's browser on a bad network must not stall the
 * inbound audio of every call of its company.
 */
@Service
public class LiveBroadcastService implements LiveUseCase {

    private static final long HEARTBEAT_MS = 15_000;

    /**
     * What happens on one call, so it is published where the call runs and nowhere else —
     * every other instance learns it over the relay. KPI and LIVE_CALLS are not: each
     * instance builds them for its own streams (LiveKpiPublisher).
     */
    private static final Set<LiveEventType> RELAYED =
            EnumSet.of(LiveEventType.TRANSCRIPT, LiveEventType.AUDIO_LEVEL, LiveEventType.NOTIFICATION);

    private final EventStreamHub<Long> streams = new EventStreamHub<>("live-sse");
    private final LiveEventRelayPort relay;

    public LiveBroadcastService(LiveEventRelayPort relay) {
        this.relay = relay;
        relay.subscribe(event -> publishLocally(event.companyId(), event.type(), event.payload()));
    }

    @Override
    public EventSubscription subscribe(long companyId, EventSink sink) {
        return streams.subscribe(companyId, sink);
    }

    @Override
    public void publish(long companyId, LiveEventType type, Object payload) {
        publishLocally(companyId, type, payload);
        if (RELAYED.contains(type)) {
            relay.publish(new LiveRelayEvent(companyId, type, payload));
        }
    }

    private void publishLocally(long companyId, LiveEventType type, Object payload) {
        streams.publish(streamCompanyId -> streamCompanyId == companyId, type.name(), payload);
    }

    /** Id of the most recent subscription, so {@link LiveKpiPublisher} can notice a new client. */
    public long findLastSubscriptionId() {
        return streams.findLastSubscriptionId();
    }

    /** Every company with at least one stream open right now. */
    public Set<Long> findSubscribedCompanyIds() {
        return new HashSet<>(streams.findAudiences());
    }

    /**
     * A takeover or whisper from the operator screen, on the company's live panels — as the
     * {@code LiveNotification(kind, text, channelId)} that screen has always sent.
     */
    @EventListener
    public void publishOperatorNotification(OperatorNotificationEvent event) {
        publish(event.companyId(), LiveEventType.NOTIFICATION,
                new LiveNotification(event.kind(), event.text(), event.channelId()));
    }

    @Scheduled(fixedRate = HEARTBEAT_MS)
    void heartbeat() {
        streams.sendHeartbeat();
    }
}
