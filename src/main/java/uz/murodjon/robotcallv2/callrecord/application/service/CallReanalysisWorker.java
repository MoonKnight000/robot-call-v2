package uz.murodjon.robotcallv2.callrecord.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.summary.SummarizedCall;
import uz.murodjon.robotcallv2.agent.summary.SummaryService;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.billing.domain.entity.CallUsage;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallReanalysisJobRepository;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallResultRepository;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallTranscriptRepository;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallReanalysisJob;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallReanalysisTarget;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallReanalysisStatus;
import uz.murodjon.robotcallv2.callrecord.domain.service.OutcomeAnalysisMerger;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Goes through the oldest active {@link CallReanalysisJob} a batch per tick, on one instance
 * at a time. For each call: the transcript is analysed again with the agent's current
 * settings, the analysis keys of the stored outcome are replaced, and the tokens are charged.
 * Progress is written after every call, so a restart or a cancel loses at most the call in
 * hand, and a cancel is noticed before the next one.
 */
@Service
public class CallReanalysisWorker {

    private static final Logger log = LoggerFactory.getLogger(CallReanalysisWorker.class);

    /** Calls per tick: each is one model request of up to {@code voice-agent.summary.timeout-ms}. */
    private static final int BATCH = 10;

    private final CallReanalysisJobRepository jobRepository;
    private final CallResultRepository callResultRepository;
    private final CallTranscriptRepository callTranscriptRepository;
    private final AiAgentUseCase aiAgentUseCase;
    private final CallBillingUseCase callBillingUseCase;
    private final SummaryService summaryService;
    private final ClusterJobLock clusterJobLock;
    private final Clock clock;

    public CallReanalysisWorker(CallReanalysisJobRepository jobRepository, CallResultRepository callResultRepository,
                                CallTranscriptRepository callTranscriptRepository, AiAgentUseCase aiAgentUseCase,
                                CallBillingUseCase callBillingUseCase, SummaryService summaryService,
                                ClusterJobLock clusterJobLock, Clock clock) {
        this.jobRepository = jobRepository;
        this.callResultRepository = callResultRepository;
        this.callTranscriptRepository = callTranscriptRepository;
        this.aiAgentUseCase = aiAgentUseCase;
        this.callBillingUseCase = callBillingUseCase;
        this.summaryService = summaryService;
        this.clusterJobLock = clusterJobLock;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${voice-agent.reanalysis.check-ms:15000}", initialDelay = 30_000)
    public void runNextBatch() {
        clusterJobLock.runExclusively("call-reanalysis", this::processNextBatch);
    }

    void processNextBatch() {
        CallReanalysisJob job = jobRepository.findNextActive();
        if (job == null) {
            return;
        }
        Progress progress = new Progress(job, clock.instant());
        AiAgent agent = aiAgentUseCase.findAgent(job.companyId(), job.aiAgentId());
        if (agent == null) {
            progress.finish(CallReanalysisStatus.FAILED, clock.instant(), "the agent was deleted");
            jobRepository.updateProgress(progress.toJob());
            return;
        }
        List<String> analysisKeys = SummaryService.findAnalysisKeys(agent);
        List<CallReanalysisTarget> targets = callResultRepository.findReanalysisTargets(job.companyId(),
                job.aiAgentId(), job.from(), job.to(), job.lastCallId(), BATCH);
        for (CallReanalysisTarget target : targets) {
            if (!callBillingUseCase.hasBalanceForCall(job.companyId())) {
                progress.finish(CallReanalysisStatus.FAILED, clock.instant(), "the balance ran out");
                jobRepository.updateProgress(progress.toJob());
                log.info("Reanalysis {} stopped at call {}: company {} is out of balance",
                        job.id(), target.callId(), job.companyId());
                return;
            }
            reanalyse(job, agent, analysisKeys, target, progress);
            if (!jobRepository.updateProgress(progress.toJob())) {
                log.info("Reanalysis {} was cancelled after call {}", job.id(), target.callId());
                return;
            }
        }
        if (targets.size() < BATCH) {
            progress.finish(CallReanalysisStatus.COMPLETED, clock.instant(), null);
            jobRepository.updateProgress(progress.toJob());
            log.info("Reanalysis {} completed: {} call(s), {} updated, {} failed",
                    job.id(), progress.processed, progress.updated, progress.failed);
        }
    }

    /** One call; a failure of any kind leaves its outcome as it was and counts it as failed. */
    private void reanalyse(CallReanalysisJob job, AiAgent agent, List<String> analysisKeys,
                           CallReanalysisTarget target, Progress progress) {
        long callId = target.callId();
        progress.lastCallId = callId;
        progress.processed++;
        try {
            Map<String, Object> stored = callResultRepository.findOutcomeByCallId(callId);
            if (stored == null) {
                progress.failed++;
                return;
            }
            String transcript = String.join("\n", callTranscriptRepository.findTranscriptLines(callId));
            SummarizedCall summarized = summaryService.summarize(job.companyId(), transcript, null, agent,
                    LocalDateTime.ofInstant(target.startedAt(), ZoneId.systemDefault()));
            if (summarized == null) {
                progress.failed++;
                return;
            }
            progress.promptTokens += summarized.promptTokens();
            progress.completionTokens += summarized.completionTokens();
            CallUsage usage = new CallUsage(0, summarized.promptTokens(), summarized.completionTokens(),
                    summarized.cachedTokens(), 0);
            progress.chargedUzs += callBillingUseCase.chargeReanalysis(job.companyId(), callId, job.id(),
                    summarized.ownLlm() ? usage.withoutLlm() : usage);
            callResultRepository.updateOutcome(callId,
                    OutcomeAnalysisMerger.mergeAnalysis(stored, summarized.summary().outcome(), analysisKeys));
            progress.updated++;
        } catch (RuntimeException e) {
            progress.failed++;
            log.warn("Reanalysis {} failed on call {}: {}", job.id(), callId, e.getMessage());
        }
    }

    /** The job's counters while a batch runs; written back after every call. */
    private static final class Progress {

        private final CallReanalysisJob job;
        private final Instant startedAt;
        private CallReanalysisStatus status = CallReanalysisStatus.RUNNING;
        private int processed;
        private int updated;
        private int failed;
        private long promptTokens;
        private long completionTokens;
        private long chargedUzs;
        private long lastCallId;
        private String lastError;
        private Instant finishedAt;

        private Progress(CallReanalysisJob job, Instant now) {
            this.job = job;
            this.startedAt = job.startedAt() != null ? job.startedAt() : now;
            this.processed = job.processedCalls();
            this.updated = job.updatedCalls();
            this.failed = job.failedCalls();
            this.promptTokens = job.promptTokens();
            this.completionTokens = job.completionTokens();
            this.chargedUzs = job.chargedUzs();
            this.lastCallId = job.lastCallId();
        }

        private void finish(CallReanalysisStatus finalStatus, Instant now, String error) {
            this.status = finalStatus;
            this.finishedAt = now;
            this.lastError = error;
        }

        private CallReanalysisJob toJob() {
            return new CallReanalysisJob(job.id(), job.companyId(), job.aiAgentId(), job.from(), job.to(), status,
                    job.totalCalls(), processed, updated, failed, promptTokens, completionTokens, chargedUzs,
                    lastCallId, lastError, job.createdAt(), startedAt, finishedAt);
        }
    }
}
