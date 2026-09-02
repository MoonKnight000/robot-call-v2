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
