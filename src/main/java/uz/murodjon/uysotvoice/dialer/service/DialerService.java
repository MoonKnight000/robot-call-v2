package uz.murodjon.uysotvoice.dialer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.agent.ari.AriService;
import uz.murodjon.uysotvoice.agent.lifecycle.GracefulShutdownManager;
import uz.murodjon.uysotvoice.campaign.dto.Campaign;
import uz.murodjon.uysotvoice.campaign.dto.CampaignTarget;
import uz.murodjon.uysotvoice.campaign.repository.CampaignRepository;
import uz.murodjon.uysotvoice.campaign.repository.CampaignTargetRepository;
import uz.murodjon.uysotvoice.campaign.service.CampaignService;
import uz.murodjon.uysotvoice.company.dto.CompanyConfig;
import uz.murodjon.uysotvoice.company.service.CompanyConfigService;
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
    private final CompanyConfigService companyConfig;
    private final RabbitTemplate rabbit;
    private final DialerState state;
    private final OutboundCallRegistry registry;
    private final AriService ariService;
    private final GracefulShutdownManager shutdown;
    private final Clock clock;

    public DialerService(DialerProperties props, CampaignRepository campaigns,
                         CampaignTargetRepository targets, CampaignService campaignService,
                         CompanyConfigService companyConfig,
                         RabbitTemplate rabbit, DialerState state,
                         OutboundCallRegistry registry, AriService ariService,
                         GracefulShutdownManager shutdown, Clock clock) {
        this.props = props;
        this.campaigns = campaigns;
        this.targets = targets;
        this.campaignService = campaignService;
        this.companyConfig = companyConfig;
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
        List<Campaign> active = campaigns.findActive();
        if (active.isEmpty()) {
            return;
        }
        // Share the tick's budget across campaigns instead of letting the first one
        // consume it: with a per-campaign batch of the full budget, campaign #1 keeps
        // the dialer saturated and #2 never gets a call out.
        int perCampaign = Math.max(1, props.dispatchBatch() / active.size());

        LocalDate today = LocalDate.now(clock);
        for (Campaign campaign : active) {
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
            List<CampaignTarget> due = targets.claimDue(campaign.id(), batch);
            for (CampaignTarget t : due) {
                state.reserve();
                state.countDispatch(campaign.id(), today);
                String language = t.language() != null ? t.language() : campaign.defaultLanguage();
                rabbit.convertAndSend(RabbitConfig.CALL_TASK_QUEUE,
                        new CallTask(campaign.id(), t.id(), t.clientId(), t.phone(), language,
                                campaign.ttsVoice(), t.contextData(), campaign.scenarioId(), campaign.companyId(),
                                campaign.disclosureEnabled()));
                log.info("Dispatched target {} ({}) of campaign {}", t.id(), t.phone(), campaign.id());
            }
        }
    }

    /**
     * How many more calls this campaign may dial today (§C14). {@link Integer#MAX_VALUE}
     * when it has no cap, so the caller's {@code Math.min} leaves the batch untouched.
     */
    private int remainingToday(Campaign campaign, LocalDate today) {
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
     * time-only check would have the bot calling debtors on a Sunday morning), AND
     * inside its company's own dial window — a STRICT ceiling on top of the campaign's
     * own (ROADMAP B.3): a campaign can never dial outside hours its company allows,
     * even if the campaign row itself says otherwise.
     */
    private boolean withinWindow(Campaign campaign) {
        if (!campaign.allowedDays().contains(LocalDate.now(clock).getDayOfWeek())) {
            return false;
        }
        LocalTime now = LocalTime.now(clock);
        if (!withinRange(now, campaign.dialWindowStart(), campaign.dialWindowEnd())) {
            return false;
        }
        // A company with no config row yet (shouldn't happen post-migration, but the
        // dialer must never hard-fail on it) imposes no additional ceiling.
        CompanyConfig config = companyConfig.find(campaign.companyId());
        return config == null || withinRange(now, config.dialWindowStart(), config.dialWindowEnd());
    }

    private static boolean withinRange(LocalTime now, LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            return true;
        }
        return !now.isBefore(start) && now.isBefore(end);
    }
}
