package uz.murodjon.robotcallv2.dialer.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.lifecycle.GracefulShutdownManager;
import uz.murodjon.robotcallv2.agent.tts.TtsWarmup;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignTargetRepository;
import uz.murodjon.robotcallv2.campaign.application.service.CampaignService;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.company.application.service.CompanyConfigService;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.dialer.application.dto.CallTask;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.dialer.infrastructure.config.DialerProperties;
import uz.murodjon.robotcallv2.dialer.infrastructure.config.RabbitConfig;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.siptrunk.application.dto.SipTrunkRow;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.SipTrunkUseCase;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final SipTrunkUseCase sipTrunks;
    private final RabbitTemplate rabbit;
    private final DialerState state;
    private final OutboundCallRegistry registry;
    private final AriService ariService;
    private final GracefulShutdownManager shutdown;
    private final TtsWarmup ttsWarmup;
    private final Clock clock;

    private final Map<Long, AtomicInteger> campaignTrunkCounters = new ConcurrentHashMap<>();

    public DialerService(DialerProperties props, CampaignRepository campaigns,
                         CampaignTargetRepository targets, CampaignService campaignService,
                         CompanyConfigService companyConfig, SipTrunkUseCase sipTrunks,
                         RabbitTemplate rabbit, DialerState state,
                         OutboundCallRegistry registry, AriService ariService,
                         GracefulShutdownManager shutdown, TtsWarmup ttsWarmup, Clock clock) {
        this.props = props;
        this.campaigns = campaigns;
        this.targets = targets;
        this.campaignService = campaignService;
        this.companyConfig = companyConfig;
        this.sipTrunks = sipTrunks;
        this.rabbit = rabbit;
        this.state = state;
        this.registry = registry;
        this.ariService = ariService;
        this.shutdown = shutdown;
        this.ttsWarmup = ttsWarmup;
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
        int perCampaign = Math.max(1, props.dispatchBatch() / active.size());

        LocalDate today = LocalDate.now(clock);
        for (Campaign campaign : active) {
            if (!withinWindow(campaign)) {
                log.debug("Campaign {} ({}) skipped: outside dial window or allowed days", campaign.id(), campaign.name());
                continue;
            }
            // Pre-warm campaign phrases on-demand if not already done
            ttsWarmup.warmUpForCampaign(campaign);

            int free = props.maxConcurrentCalls() - state.active();
            if (free <= 0) {
                log.debug("Dialer concurrency saturated (active={}/max={})", state.active(), props.maxConcurrentCalls());
                return; // saturated — try again next tick
            }
            int batch = Math.min(free, perCampaign);
            batch = Math.min(batch, remainingToday(campaign, today));
            if (batch <= 0) {
                continue; // campaign has spent its day (§C14)
            }
            List<CampaignTarget> due = targets.claimDue(campaign.id(), batch);
            if (due.isEmpty()) {
                log.debug("Campaign {} has no due targets to claim", campaign.id());
                continue;
            }

            List<SipTrunkRow> candidateTrunks = sipTrunks.findTrunksForCall(campaign.companyId(), campaign.sipTrunkIdsOrEmpty());
            AtomicInteger counter = campaignTrunkCounters.computeIfAbsent(campaign.id(), k -> new AtomicInteger(0));

            for (CampaignTarget t : due) {
                state.reserve();
                state.countDispatch(campaign.id(), today);
                String language = t.language() != null ? t.language() : campaign.defaultLanguage();

                Long selectedTrunkId = null;
                if (!candidateTrunks.isEmpty()) {
                    int idx = Math.abs(counter.getAndIncrement() % candidateTrunks.size());
                    selectedTrunkId = candidateTrunks.get(idx).id();
                }

                rabbit.convertAndSend(RabbitConfig.CALL_TASK_QUEUE,
                        new CallTask(campaign.id(), t.id(), t.clientId(), t.phone(), language,
                                campaign.ttsVoice(), t.contextData(), campaign.scenarioId(), campaign.companyId(),
                                campaign.disclosureEnabled(), campaign.ambientSound(), campaign.midCallSmsEnabled(),
                                campaign.midCallSmsTemplate(), campaign.voicemailAction(), campaign.voicemailMessage(),
                                campaign.dtmfInputEnabled(), campaign.emotionAdaptiveVoice(),
                                campaign.languageVoices(), selectedTrunkId));
                log.info("Dispatched target {} ({}) of campaign {} via trunk {}", t.id(), t.phone(), campaign.id(), selectedTrunkId);
            }
        }
    }

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

    private boolean withinWindow(Campaign campaign) {
        Set<DayOfWeek> days = campaign.allowedDays();
        if (days != null && !days.isEmpty() && !days.contains(LocalDate.now(clock).getDayOfWeek())) {
            return false;
        }
        LocalTime now = LocalTime.now(clock);
        if (!withinRange(now, campaign.dialWindowStart(), campaign.dialWindowEnd())) {
            return false;
        }
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
