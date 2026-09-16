package uz.murodjon.robotcallv2.live.application.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.callrecord.application.dto.LiveCallRow;
import uz.murodjon.robotcallv2.live.domain.entity.LiveKpiSnapshot;
import uz.murodjon.robotcallv2.live.domain.enums.LiveEventType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodically re-reads existing, already-computed state — per company, so every stream
 * sees its own calls and its own count and nothing of another tenant's.
 */
@Component
public class LiveKpiPublisher {

    private final AriService ariService;
    private final LiveBroadcastService broadcast;

    private final Map<Long, LiveKpiSnapshot> lastKpi = new ConcurrentHashMap<>();
    private final Map<Long, List<LiveCallRow>> lastCalls = new ConcurrentHashMap<>();
    private volatile long lastSubscriptionId;

    public LiveKpiPublisher(AriService ariService, LiveBroadcastService broadcast) {
        this.ariService = ariService;
        this.broadcast = broadcast;
    }

    @Scheduled(fixedRateString = "${voice-agent.live.kpi-poll-ms:2000}")
    void tick() {
        // Only changes are published, so a client that subscribed after the last change
        // would sit on an empty screen until the next one — which, with no call running,
        // never comes. Forget the diff baseline whenever someone new connects.
        long subscriptionId = broadcast.findLastSubscriptionId();
        if (subscriptionId != lastSubscriptionId) {
            lastSubscriptionId = subscriptionId;
            lastKpi.clear();
            lastCalls.clear();
        }

        Set<Long> companies = broadcast.findSubscribedCompanyIds();
        lastKpi.keySet().retainAll(companies);
        lastCalls.keySet().retainAll(companies);
        for (long companyId : companies) {
            List<LiveCallRow> calls = ariService.findLiveCalls(companyId);
            LiveKpiSnapshot kpi = new LiveKpiSnapshot(calls.size());
            if (!kpi.equals(lastKpi.put(companyId, kpi))) {
                broadcast.publish(companyId, LiveEventType.KPI, kpi);
            }
            if (!calls.equals(lastCalls.put(companyId, calls))) {
                broadcast.publish(companyId, LiveEventType.LIVE_CALLS, calls);
            }
        }
    }
}
