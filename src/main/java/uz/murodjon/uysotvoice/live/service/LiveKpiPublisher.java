package uz.murodjon.uysotvoice.live.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.agent.ari.AriService;
import uz.murodjon.uysotvoice.agent.metrics.VoiceMetrics;
import uz.murodjon.uysotvoice.callrecord.dto.LiveCallRow;
import uz.murodjon.uysotvoice.live.enums.LiveEventType;
import uz.murodjon.uysotvoice.live.dto.LiveKpiSnapshot;

import java.util.List;

/**
 * Periodically re-reads existing, already-computed state — {@link VoiceMetrics}'
 * active-call gauge and {@link AriService#liveCalls()} — and publishes it only when it
 * changed since the last tick, rather than pushing an identical snapshot to every
 * connected client several times a minute for no reason.
 *
 * <p>No new source of truth is introduced here: both reads go straight to the same
 * places {@code GET /api/calls/live} and the Prometheus gauge already draw from.
 */
@Component
public class LiveKpiPublisher {

    private final AriService ariService;
    private final VoiceMetrics metrics;
    private final LiveBroadcastService broadcast;

    private volatile LiveKpiSnapshot lastKpi;
    private volatile List<LiveCallRow> lastCalls = List.of();

    public LiveKpiPublisher(AriService ariService, VoiceMetrics metrics, LiveBroadcastService broadcast) {
        this.ariService = ariService;
        this.metrics = metrics;
        this.broadcast = broadcast;
    }

    @Scheduled(fixedRateString = "${voice-agent.live.kpi-poll-ms:2000}")
    void tick() {
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
