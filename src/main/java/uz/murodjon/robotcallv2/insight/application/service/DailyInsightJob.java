package uz.murodjon.robotcallv2.insight.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.insight.application.port.output.AgentInsightRepository;
import uz.murodjon.robotcallv2.insight.application.port.output.InsightClustererPort;
import uz.murodjon.robotcallv2.insight.domain.entity.AgentCallSignals;
import uz.murodjon.robotcallv2.insight.domain.entity.AgentInsight;
import uz.murodjon.robotcallv2.insight.domain.entity.InsightProposal;
import uz.murodjon.robotcallv2.insight.infrastructure.config.InsightProperties;
import uz.murodjon.robotcallv2.quality.application.port.input.CallQualityUseCase;
import uz.murodjon.robotcallv2.quality.domain.entity.CallQuality;
import uz.murodjon.robotcallv2.quality.domain.entity.ScoredAgent;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Once a day, reads what each agent's calls said about it and proposes what to change
 * (UNIVERSAL-OPERATOR-PLAN D.3).
 *
 * <p>The loop's only unattended step, and deliberately the least powerful one: it writes to
 * an inbox and nothing else. What it reads is already there — the judge's flags, the
 * reviewers' marks, the tool errors — and the model's whole job is to say which of them are
 * the same problem seen thirty times.
 *
 * <p>Early in the morning, so an operator finds the night's proposals waiting rather than
 * watching them appear during the day.
 */
@Service
public class DailyInsightJob {

    private static final Logger log = LoggerFactory.getLogger(DailyInsightJob.class);

    private final CallQualityUseCase callQualityUseCase;
    private final AiAgentUseCase aiAgentUseCase;
    private final InsightClustererPort clusterer;
    private final AgentInsightRepository repository;
    /** One sweep at a time across the cluster — two would propose everything twice. */
    private final ClusterJobLock clusterJobLock;
    private final InsightProperties insightProperties;
    private final Clock clock;

    public DailyInsightJob(CallQualityUseCase callQualityUseCase,
                           AiAgentUseCase aiAgentUseCase,
                           InsightClustererPort clusterer,
                           AgentInsightRepository repository,
                           ClusterJobLock clusterJobLock,
                           InsightProperties insightProperties,
                           Clock clock) {
        this.callQualityUseCase = callQualityUseCase;
        this.aiAgentUseCase = aiAgentUseCase;
        this.clusterer = clusterer;
        this.repository = repository;
        this.clusterJobLock = clusterJobLock;
        this.insightProperties = insightProperties;
        this.clock = clock;
    }

    @Scheduled(cron = "${voice-agent.insight.cron:0 20 6 * * *}", zone = "Asia/Tashkent")
    public void proposeDailyInsights() {
        if (!insightProperties.enabled()) {
            return;
        }
        clusterJobLock.runExclusively("insight.daily", this::sweep);
    }

    private void sweep() {
        Instant since = clock.instant().minus(Duration.ofHours(insightProperties.windowHours()));
        for (ScoredAgent scored : callQualityUseCase.findAgentsScoredSince(since)) {
            if (scored.calls() < insightProperties.minCalls()) {
                continue;
            }
            try {
                proposeForAgent(scored, since);
            } catch (RuntimeException e) {
                // One company's agent — a deleted one, a model that timed out — must not
                // stop the sweep before the tenants after it.
                log.warn("[company {}] Insights for agent {} could not be made: {}",
                        scored.companyId(), scored.aiAgentId(), e.getMessage());
            }
        }
    }

    private void proposeForAgent(ScoredAgent scored, Instant since) {
        List<CallQuality> rows =
                callQualityUseCase.findByAgentSince(scored.companyId(), scored.aiAgentId(), since);
        AgentCallSignals signals = AgentCallSignals.of(rows);
        if (!signals.worthClustering()) {
            return;
        }
        AiAgent agent = aiAgentUseCase.findAgent(scored.companyId(), scored.aiAgentId());
        if (agent == null) {
            return;
        }
        int written = 0;
        for (InsightProposal proposal : clusterer.cluster(agent.name(), agent.language(), signals)) {
            if (written >= insightProperties.maxPerAgent()) {
                break;
            }
            // The window overlaps yesterday's, so the same finding comes back until it is
            // decided. It goes in the inbox once.
            if (repository.existsProposed(scored.aiAgentId(), proposal.type(), proposal.title())) {
                continue;
            }
            repository.save(AgentInsight.proposed(scored.companyId(), scored.aiAgentId(), proposal));
            written++;
        }
        if (written > 0) {
            log.info("[company {}] Agent {} ({} calls): {} insight(s) proposed",
                    scored.companyId(), scored.aiAgentId(), signals.calls(), written);
        }
    }
}
