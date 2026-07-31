package uz.murodjon.uysotvoice.dialer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.agent.ari.AriService;
import uz.murodjon.uysotvoice.agent.lifecycle.GracefulShutdownManager;
import uz.murodjon.uysotvoice.campaign.dto.CampaignRow;
import uz.murodjon.uysotvoice.campaign.dto.TargetRow;
import uz.murodjon.uysotvoice.campaign.repository.CampaignRepository;
import uz.murodjon.uysotvoice.campaign.repository.CampaignTargetRepository;
import uz.murodjon.uysotvoice.campaign.service.CampaignService;
import uz.murodjon.uysotvoice.dialer.config.DialerProperties;
import uz.murodjon.uysotvoice.dialer.config.RabbitConfig;
import uz.murodjon.uysotvoice.dialer.dto.CallTask;
import uz.murodjon.uysotvoice.dialer.dto.OutboundCall;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
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
    private final Clock clock;

    public DialerService(DialerProperties props, CampaignRepository campaigns,
                         CampaignTargetRepository targets, CampaignService campaignService,
                         RabbitTemplate rabbit, DialerState state,
                         OutboundCallRegistry registry, AriService ariService,
                         GracefulShutdownManager shutdown, Clock clock) {
        this.props = props;
        this.campaigns = campaigns;
        this.targets = targets;
        this.campaignService = campaignService;
        this.rabbit = rabbit;
        this.state = state;
        this.registry = registry;
        this.ariService = ariService;
        this.shutdown = shutdown;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "#{${voice-agent.dialer.tick-seconds:5} * 1000}")
    public void dispatch() {
        if (!props.enabled() || shutdown.isDraining()) {
            return; // no new calls while shutting down
        }
        List<CampaignRow> active = campaigns.findActive();
        if (active.isEmpty()) {
            return;
        }
        // Share the tick's budget across campaigns instead of letting the first one
        // consume it: with a per-campaign batch of the full budget, campaign #1 keeps
        // the dialer saturated and #2 never gets a call out.
        int perCampaign = Math.max(1, props.dispatchBatch() / active.size());

        LocalDate today = LocalDate.now(clock);
        for (CampaignRow campaign : active) {
            if (!withinWindow(campaign)) {
                continue;
            }
            int free = props.maxConcurrentCalls() - state.active();
            if (free <= 0) {
                return; // saturated — try again next tick
            }
            int batch = Math.min(free, perCampaign);
            batch = Math.min(batch, remainingToday(campaign, today));
            if (batch <= 0) {
                continue; // campaign has spent its day (§C14)
            }
            // Claimed and marked IN_PROGRESS in one statement — see claimDue: two
            // statements would let another instance dial the same client.
            List<TargetRow> due = targets.claimDue(campaign.id(), batch);
            for (TargetRow t : due) {
                state.reserve();
                state.countDispatch(campaign.id(), today);
                String language = t.language() != null ? t.language() : campaign.defaultLanguage();
                rabbit.convertAndSend(RabbitConfig.CALL_TASK_QUEUE,
                        new CallTask(campaign.id(), t.id(), t.clientId(), t.phone(), language,
                                campaign.ttsVoice(), t.contextData()));
                log.info("Dispatched target {} ({}) of campaign {}", t.id(), t.phone(), campaign.id());
            }
        }
    }

    /**
     * How many more calls this campaign may dial today (§C14). {@link Integer#MAX_VALUE}
     * when it has no cap, so the caller's {@code Math.min} leaves the batch untouched.
     */
    private int remainingToday(CampaignRow campaign, LocalDate today) {
        int cap = campaign.dailyCallCap();
        if (cap <= 0) {
            return Integer.MAX_VALUE;
        }
        int used = state.dispatchedToday(campaign.id(), today);
        int remaining = cap - used;
        if (remaining <= 0) {
            log.info("Campaign {} reached its daily cap ({} calls) — pausing until tomorrow",
                    campaign.id(), cap);
        }
        return remaining;
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

    /**
     * Whether the campaign may dial right now: inside its time-of-day window and on a
     * weekday it is allowed to call (§11.2 — weekends are configured separately, so a
     * time-only check would have the bot calling debtors on a Sunday morning).
     */
    private boolean withinWindow(CampaignRow campaign) {
        if (!campaign.allowedDays().contains(LocalDate.now(clock).getDayOfWeek())) {
            return false;
        }
        LocalTime start = campaign.dialWindowStart();
        LocalTime end = campaign.dialWindowEnd();
        if (start == null || end == null) {
            return true;
        }
        LocalTime now = LocalTime.now(clock);
        return !now.isBefore(start) && now.isBefore(end);
    }
}
