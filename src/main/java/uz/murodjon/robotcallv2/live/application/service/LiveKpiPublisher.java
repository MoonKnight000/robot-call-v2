package uz.murodjon.robotcallv2.live.application.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics;
import uz.murodjon.robotcallv2.callrecord.application.dto.LiveCallRow;
import uz.murodjon.robotcallv2.live.domain.entity.LiveKpiSnapshot;
import uz.murodjon.robotcallv2.live.domain.enums.LiveEventType;

import java.util.List;

/**
 * Periodically re-reads existing, already-computed state.
 */
@Component
public class LiveKpiPublisher {

    private final AriService ariService;
    private final VoiceMetrics metrics;
    private final LiveBroadcastService broadcast;

    private volatile LiveKpiSnapshot lastKpi;
    private volatile List<LiveCallRow> lastCalls = List.of();
    private volatile long lastSubscriptionId;

    public LiveKpiPublisher(AriService ariService, VoiceMetrics metrics, LiveBroadcastService broadcast) {
        this.ariService = ariService;
        this.metrics = metrics;
        this.broadcast = broadcast;
    }

    @Scheduled(fixedRateString = "${voice-agent.live.kpi-poll-ms:2000}")
    void tick() {
        // Only changes are published, so a client that subscribed after the last change
        // would sit on an empty screen until the next one — which, with no call running,
        // never comes. Forget the diff baseline whenever someone new connects.
        long subscriptionId = broadcast.lastSubscriptionId();
        if (subscriptionId != lastSubscriptionId) {
            lastSubscriptionId = subscriptionId;
            lastKpi = null;
            lastCalls = null;
        }

        LiveKpiSnapshot kpi = new LiveKpiSnapshot(metrics.activeCalls());
        if (!kpi.equals(lastKpi)) {
            lastKpi = kpi;
            broadcast.publish(LiveEventType.KPI, kpi);
        }

        List<LiveCallRow> calls = ariService.liveCalls();
        if (!calls.equals(lastCalls)) {
            lastCalls = calls;
            broadcast.publish(LiveEventType.LIVE_CALLS, calls);
        }
    }
}
