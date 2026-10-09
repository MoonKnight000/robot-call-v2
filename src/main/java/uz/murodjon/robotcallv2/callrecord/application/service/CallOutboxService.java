package uz.murodjon.robotcallv2.callrecord.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.agent.summary.SummarizedCall;
import uz.murodjon.robotcallv2.agent.summary.SummaryService;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallOutboxRepository;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallAttempt;
import uz.murodjon.robotcallv2.callrecord.domain.entity.PendingSummary;
import uz.murodjon.robotcallv2.callrecord.infrastructure.config.OutboxProperties;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.scenario.application.port.input.ScenarioUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Summarizes the calls whose first summary failed (PROJECT.md §4.3, Stage 9). The CRM writes
 * a summary sets off are delivered, and retried, by {@link OutboxDeliveryService}.
 */
@Service
public class CallOutboxService {

    private static final Logger log = LoggerFactory.getLogger(CallOutboxService.class);

    /**
     * How far back the summary retry looks. The tries are minutes apart, so a call older
     * than this is summarized or given up on; unbounded, every poll read the whole history.
     */
    private static final Duration SUMMARY_RETRY_WINDOW = Duration.ofDays(1);

    private final CallOutboxRepository repository;
    /** The call's agent, for the analysis it asks of the summary. */
    private final AiAgentUseCase aiAgentUseCase;
    private final CallRecordService callRecordService;
    private final SummaryService summaryService;
    private final ScenarioUseCase scenarioUseCase;
    /** What a summary sets off — shared with the first pass, so a retry does all of it. */
    private final CallFinalizer callFinalizer;
    /** Settles the target on a promise the retried summary found. */
    private final CampaignTargetUseCase campaignTargetUseCase;
    private final ClusterJobLock clusterJobLock;
    private final OutboxProperties outboxProperties;
    private final Clock clock;

    public CallOutboxService(CallOutboxRepository repository,
                             AiAgentUseCase aiAgentUseCase,
                             CallRecordService callRecordService,
                             SummaryService summaryService,
                             ScenarioUseCase scenarioUseCase,
                             CallFinalizer callFinalizer,
                             CampaignTargetUseCase campaignTargetUseCase,
                             ClusterJobLock clusterJobLock,
                             OutboxProperties outboxProperties,
                             Clock clock) {
        this.repository = repository;
        this.aiAgentUseCase = aiAgentUseCase;
        this.callRecordService = callRecordService;
        this.summaryService = summaryService;
        this.scenarioUseCase = scenarioUseCase;
        this.callFinalizer = callFinalizer;
        this.campaignTargetUseCase = campaignTargetUseCase;
        this.clusterJobLock = clusterJobLock;
        this.outboxProperties = outboxProperties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "#{${voice-agent.outbox.interval-minutes:5} * 60 * 1000}",
            initialDelay = 60_000)
    public void sweep() {
        if (!outboxProperties.enabled()) {
            return;
        }
        // One instance at a time: two sweeps reading the same pending rows summarized a call
        // twice (two LLM bills). The claim in retrySummaries keeps that true on its own too.
        clusterJobLock.runExclusively("callrecord.outbox-sweep", this::retrySummaries);
    }

    /**
     * Summarize calls whose first attempt produced nothing.
     *
     * <p>Claimed, not just read: each call has its attempt counted and stays hidden from every
     * instance's sweep for one interval, so no two instances summarize the same call at once,
     * and a call whose summary is still running is not picked up again.
     *
     * <p>Once a summary exists, the call gets exactly what the first pass would have given
     * it: a promise the summary found becomes the call's outcome and settles its target
     * (rather than leaving a debtor who promised to pay on the retry list), and the note,
     * result, memory, callback and post-call actions run through {@link CallFinalizer}.
     */
    private void retrySummaries() {
        Instant now = clock.instant();
        List<PendingSummary> pending;
        try {
            pending = repository.claimAttemptsAwaitingSummary(outboxProperties.maxAttempts(),
                    now.minus(SUMMARY_RETRY_WINDOW), now,
                    now.plus(Duration.ofMinutes(outboxProperties.intervalMinutes())), outboxProperties.batch());
        } catch (Exception e) {
            log.warn("Outbox: claiming calls awaiting a summary failed: {}", e.getMessage());
            return;
        }
        if (pending.isEmpty()) {
            return;
        }
        log.info("Outbox: {} call(s) awaiting a summary", pending.size());
        for (PendingSummary p : pending) {
            try {
                String transcript = callRecordService.findTranscriptText(p.callId());
                long companyId = callRecordService.findCompanyIdByAttemptId(p.callId());
                Scenario scenario = scenarioUseCase.requireScenario(companyId, p.scenarioId());
                AiAgent agent = aiAgentUseCase.findAgent(companyId, p.agentId()) != null
                        ? aiAgentUseCase.requirePublishedAgent(companyId, p.agentId()) : null;
                SummarizedCall summarized = summaryService.summarize(companyId, transcript, scenario.definition(), agent);
                if (summarized == null) {
                    log.debug("Outbox: summary still unavailable for call {}", p.callId());
                    continue;
                }
                CallSummary summary = summarized.summary();
                Disposition disposition = p.disposition();
                if (disposition == null && CallFinalizer.promised(summary)) {
                    disposition = Disposition.PROMISE_TO_PAY;
                    callRecordService.updateDisposition(p.callId(), disposition);
                    long targetId = callRecordService.findTargetIdByAttemptId(p.callId());
                    if (targetId != CallAttempt.NO_TARGET) {
                        campaignTargetUseCase.applyOutcome(companyId, targetId, disposition);
                    }
                }
                callFinalizer.applySummary(companyId, p.callId(), p.clientId(), callRecordService.findPhoneByAttemptId(p.callId()), agent,
                        scenario, disposition, summary, p.disposition() == Disposition.TRANSFERRED);
                // The call was settled on its first pass; the summary is charged on its own.
                callFinalizer.chargeSummary(companyId, p.callId(), summarized);
                log.info("Outbox: summarized call {} on retry (disposition={})", p.callId(), disposition);
            } catch (Exception e) {
                log.warn("Outbox: summary retry failed for call {}: {}", p.callId(), e.getMessage());
            }
        }
    }
}
