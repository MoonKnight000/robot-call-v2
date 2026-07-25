package uz.murodjon.uysotvoice.dialer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.murodjon.uysotvoice.agent.ari.AriService;
import uz.murodjon.uysotvoice.agent.lifecycle.GracefulShutdownManager;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

/**
 * The scheduled dialer (PROJECT.md §5.2, §10). Each tick it scans ACTIVE campaigns,
 * and for those inside their dial window dispatches due targets to RabbitMQ —
 * bounded by {@code dispatch-batch} (rate) and free concurrency slots
 * ({@code max-concurrent-calls}). A separate sweep reclaims calls that were
 * dispatched but never answered, freeing their slot and scheduling a retry.
 */
@Service
public class DialerService {

    private static final Logger log = LoggerFactory.getLogger(DialerService.class);

    private final DialerProperties props;
    private final CampaignRepository campaigns;
    private final CampaignTargetRepository targets;
    private final CampaignService campaignService;
    private final RabbitTemplate rabbit;
    private final DialerState state;
    private final OutboundCallRegistry registry;
    private final AriService ariService;
    private final GracefulShutdownManager shutdown;

    public DialerService(DialerProperties props, CampaignRepository campaigns,
                         CampaignTargetRepository targets, CampaignService campaignService,
                         RabbitTemplate rabbit, DialerState state,
                         OutboundCallRegistry registry, AriService ariService,
                         GracefulShutdownManager shutdown) {
        this.props = props;
        this.campaigns = campaigns;
        this.targets = targets;
        this.campaignService = campaignService;
        this.rabbit = rabbit;
        this.state = state;
        this.registry = registry;
        this.ariService = ariService;
        this.shutdown = shutdown;
    }

    @Scheduled(fixedDelayString = "#{${voice-agent.dialer.tick-seconds:5} * 1000}")
    public void dispatch() {
        if (!props.enabled() || shutdown.isDraining()) {
            return; // no new calls while shutting down
        }
        for (CampaignRow campaign : campaigns.findActive()) {
            if (!withinWindow(campaign)) {
                continue;
            }
            int free = props.maxConcurrentCalls() - state.active();
            if (free <= 0) {
                return; // saturated — try again next tick
            }
            int batch = Math.min(free, props.dispatchBatch());
            List<TargetRow> due = targets.findDue(campaign.id(), batch);
            for (TargetRow t : due) {
                targets.markInProgress(t.id());
                state.reserve();
                String language = t.language() != null ? t.language() : campaign.defaultLanguage();
                rabbit.convertAndSend(RabbitConfig.CALL_TASK_QUEUE,
                        new CallTask(campaign.id(), t.id(), t.clientId(), t.phone(), language, t.contextData()));
                log.info("Dispatched target {} ({}) of campaign {}", t.id(), t.phone(), campaign.id());
            }
        }
    }

    /** Reclaim slots for calls dispatched but never answered (schedule a retry). */
    @Scheduled(fixedDelay = 30_000)
    public void reclaimStale() {
        for (String channelId : registry.staleUnanswered(Duration.ofSeconds(props.reclaimAfterSec()))) {
            OutboundCall oc = registry.remove(channelId);
            if (oc != null) {
                state.release();
                campaignService.applyOutcome(oc.targetId(), Disposition.NO_ANSWER);
                ariService.hangupChannel(channelId);
                log.info("Reclaimed unanswered call {} (target {})", channelId, oc.targetId());
            }
        }
    }

    private boolean withinWindow(CampaignRow campaign) {
        LocalTime start = campaign.dialWindowStart();
        LocalTime end = campaign.dialWindowEnd();
        if (start == null || end == null) {
            return true;
        }
        LocalTime now = LocalTime.now();
        return !now.isBefore(start) && now.isBefore(end);
    }
}
