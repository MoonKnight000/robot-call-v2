package uz.murodjon.robotcallv2.dialer.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.lifecycle.GracefulShutdownManager;
import uz.murodjon.robotcallv2.agent.rtp.RtpProperties;
import uz.murodjon.robotcallv2.agent.tts.TtsWarmup;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignVariantUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignTargetRepository;
import uz.murodjon.robotcallv2.campaign.application.service.CampaignService;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignVariant;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.company.application.service.CompanyConfigService;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.dialer.application.dto.CallTask;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.dialer.infrastructure.config.DialerProperties;
import uz.murodjon.robotcallv2.dialer.infrastructure.config.RabbitConfig;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
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

    private final DialerProperties dialerProperties;
    private final RtpProperties rtpProperties;
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
    private final CampaignVariantUseCase campaignVariants;
    private final AiAgentUseCase aiAgents;

    private final Map<Long, AtomicInteger> campaignTrunkCounters = new ConcurrentHashMap<>();

    public DialerService(DialerProperties dialerProperties, RtpProperties rtpProperties, CampaignRepository campaigns,
                         CampaignTargetRepository targets, CampaignService campaignService,
                         CompanyConfigService companyConfig, SipTrunkUseCase sipTrunks,
                         RabbitTemplate rabbit, DialerState state,
                         OutboundCallRegistry registry, AriService ariService,
                         GracefulShutdownManager shutdown, TtsWarmup ttsWarmup, Clock clock,
                         CampaignVariantUseCase campaignVariants, AiAgentUseCase aiAgents) {
        this.dialerProperties = dialerProperties;
        this.rtpProperties = rtpProperties;
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
        this.campaignVariants = campaignVariants;
        this.aiAgents = aiAgents;
    }

    @Scheduled(fixedDelayString = "#{${voice-agent.dialer.tick-seconds:5} * 1000}")
    public void dispatch() {
        if (!dialerProperties.enabled() || shutdown.isDraining()) {
            return; // no new calls while shutting down
        }
        List<Campaign> active = campaigns.findActive();
        if (active.isEmpty()) {
            return;
        }
        int perCampaign = Math.max(1, dialerProperties.dispatchBatch() / active.size());

        LocalDate today = LocalDate.now(clock);
        for (Campaign campaign : active) {
            if (!withinWindow(campaign)) {
                log.debug("Campaign {} ({}) skipped: outside dial window or allowed days", campaign.id(), campaign.name());
                continue;
            }
            // Everything about how this campaign's calls sound. Read once per tick rather
            // than once per target: a tick dials at most `dispatch-batch` numbers and they
            // all speak as the same agent.
            AiAgent agent;
            try {
                agent = aiAgents.requireAgent(campaign.companyId(), campaign.aiAgentId());
            } catch (RuntimeException e) {
                log.error("Campaign {} ({}) skipped: {}", campaign.id(), campaign.name(), e.getMessage());
                continue;
            }
            if (!agent.enabled()) {
                log.debug("Campaign {} skipped: agent {} is disabled", campaign.id(), agent.id());
                continue;
            }

            // Pre-warm campaign phrases on-demand if not already done
            ttsWarmup.warmUpForCampaign(campaign, agent);

            // The platform's ceiling is physical: past the RTP port range a call is
            // answered and then dropped for want of a port, which costs the subscriber a
            // ring and us a connected minute.
            int freePlatform = Math.min(dialerProperties.maxConcurrentCalls(), rtpProperties.mediaCapacity())
                    - state.activeTotal();
            if (freePlatform <= 0) {
                log.debug("Platform concurrency saturated (active={}/max={})",
                        state.activeTotal(), dialerProperties.maxConcurrentCalls());
                return; // nothing can be dialled this tick, by anyone
            }
            // The company's own share. `continue`, not `return`: one tenant filling its
            // quota is not a reason for the next tenant in this sweep to dial nothing.
            int freeCompany = dialerProperties.maxConcurrentCallsPerCompany() - state.active(campaign.companyId());
            if (freeCompany <= 0) {
                log.debug("Company {} concurrency saturated (active={}/max={})", campaign.companyId(),
                        state.active(campaign.companyId()), dialerProperties.maxConcurrentCallsPerCompany());
                continue;
            }
            int free = Math.min(freePlatform, freeCompany);
            int batch = Math.min(free, perCampaign);
            batch = Math.min(batch, remainingToday(campaign, today));
            if (batch <= 0) {
                continue; // campaign has spent its day (§C14)
            }
            // Before the targets are claimed, not after: a campaign whose selected trunks
            // are all disabled has nothing to dial with, and claiming first would leave
            // the batch marked as taken with no call ever placed for it.
            List<SipTrunkRow> candidateTrunks;
            try {
                candidateTrunks = sipTrunks.findTrunksForCall(campaign.companyId(), agent.sipTrunkIdsOrEmpty());
            } catch (ConflictException e) {
                // Paused, not skipped. With no usable trunk the campaign cannot place a
                // single call, and leaving it ACTIVE shows an owner a running campaign
                // that silently dials nothing — while this loop repeats the same error
                // every few seconds. PAUSED states it once, and an operator restarts it
                // when the trunk is back, choosing then whether to dial at once.
                log.error("Campaign {} ({}) paused: {}", campaign.id(), campaign.name(), e.getMessage());
                // setStatus, not pause(): this thread serves every tenant's campaigns and
                // pause() scopes the update to the request's company, which here is the
                // platform default — another tenant's campaign would stay ACTIVE.
                campaignService.setStatus(campaign.companyId(), campaign.id(), CampaignStatus.PAUSED);
                continue;
            }

            List<CampaignTarget> due = targets.claimDue(campaign.id(), batch);
            if (due.isEmpty()) {
                log.debug("Campaign {} has no due targets to claim", campaign.id());
                continue;
            }

            AtomicInteger counter = campaignTrunkCounters.computeIfAbsent(campaign.id(), k -> new AtomicInteger(0));

            for (CampaignTarget t : due) {
                state.reserve(campaign.companyId());
                state.countDispatch(campaign.id(), today);
                String language = t.language() != null ? t.language() : agent.language();

                Long selectedTrunkId = null;
                if (!candidateTrunks.isEmpty()) {
                    int idx = Math.abs(counter.getAndIncrement() % candidateTrunks.size());
                    selectedTrunkId = candidateTrunks.get(idx).id();
                }

                // Assigned here rather than when the task is consumed: this is the one place
                // that knows the campaign is dialling, so it is also where the attempt is
                // counted against the variant. Keyed on the number, so a retry of this same
                // target lands on the same script and the two attempts do not end up
                // credited to different variants.
                CampaignVariant variant =
                        campaignVariants.findForCall(campaign.companyId(), campaign.id(), t.phone());
                Long variantId = null;
                long aiAgentId = agent.id();
                String ttsVoiceOverride = null;
                String promptOverride = null;
                if (variant != null) {
                    variantId = variant.id();
                    // A variant that names its own agent is testing a different voice,
                    // persona or script wholesale; one that only names a voice is testing
                    // that voice against the agent it otherwise shares.
                    aiAgentId = variant.aiAgentId() != null ? variant.aiAgentId() : aiAgentId;
                    ttsVoiceOverride = variant.ttsVoiceId();
                    promptOverride = variant.promptOverride();
                    campaignVariants.recordCall(variantId);
                }

                rabbit.convertAndSend(RabbitConfig.CALL_TASK_QUEUE,
                        new CallTask(campaign.id(), t.id(), t.clientId(), t.phone(), language,
                                t.contextData(), campaign.companyId(), aiAgentId, selectedTrunkId,
                                variantId, ttsVoiceOverride, promptOverride));
                log.info("Dispatched target {} ({}) of campaign {} via trunk {}{}", t.id(), t.phone(), campaign.id(),
                        selectedTrunkId, variantId != null ? ", variant " + variant.name() : "");
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
        for (String channelId : registry.staleUnanswered(Duration.ofSeconds(dialerProperties.reclaimAfterSec()))) {
            OutboundCall oc = registry.remove(channelId);
            if (oc != null) {
                state.release(oc.companyId());
                campaignService.applyOutcome(oc.companyId(), oc.targetId(), Disposition.NO_ANSWER);
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
