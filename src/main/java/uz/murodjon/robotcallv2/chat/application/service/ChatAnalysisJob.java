package uz.murodjon.robotcallv2.chat.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.agent.summary.SummarizedCall;
import uz.murodjon.robotcallv2.agent.summary.SummaryService;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.billing.domain.entity.CallUsage;
import uz.murodjon.robotcallv2.chat.application.port.output.ChatSessionRepository;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatAnalysis;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatMessage;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatSession;
import uz.murodjon.robotcallv2.chat.domain.enums.ChatRole;
import uz.murodjon.robotcallv2.chat.infrastructure.config.ChatProperties;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The post-chat pass (Retell's chat analysis): once a conversation has ended, or fell silent past
 * its TTL, its transcript goes through the same summary a call gets — the agent's
 * {@code dataNeeded}, the scenario's {@code outcomeSchema} — and the tokens are charged to the
 * company. A sweep on a timer rather than a hook at the end of a turn: a chat that simply stops
 * has no last turn to hang it on, and a reply never waits for it.
 */
@Component
public class ChatAnalysisJob {

    private static final Logger log = LoggerFactory.getLogger(ChatAnalysisJob.class);
    private static final int BATCH_SIZE = 20;

    private final ChatSessionRepository repository;
    private final AiAgentUseCase aiAgentUseCase;
    private final SummaryService summaryService;
    private final CallBillingUseCase callBillingUseCase;
    private final ChatProperties chatProperties;
    private final ClusterJobLock clusterJobLock;
    private final Clock clock;
    private final boolean enabled;

    public ChatAnalysisJob(ChatSessionRepository repository, AiAgentUseCase aiAgentUseCase,
                           SummaryService summaryService, CallBillingUseCase callBillingUseCase,
                           ChatProperties chatProperties, ClusterJobLock clusterJobLock, Clock clock,
                           @Value("${voice-agent.chat.analysis-enabled:true}") boolean enabled) {
        this.repository = repository;
        this.aiAgentUseCase = aiAgentUseCase;
        this.summaryService = summaryService;
        this.callBillingUseCase = callBillingUseCase;
        this.chatProperties = chatProperties;
        this.clusterJobLock = clusterJobLock;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${voice-agent.chat.analysis-check-ms:60000}")
    public void analyzeFinishedChats() {
        if (!enabled || !summaryService.isAvailable()) {
            return;
        }
        clusterJobLock.runExclusively("chat.analysis", this::analyzeBatch);
    }

    void analyzeBatch() {
        Instant silentBefore = clock.instant().minus(Duration.ofMinutes(chatProperties.sessionTtlMinutes()));
        for (ChatSession session : repository.findSessionsToAnalyze(silentBefore, BATCH_SIZE)) {
            try {
                repository.updateAnalysis(session.id(), analyze(session));
            } catch (RuntimeException e) {
                // Recorded as failed so one broken chat does not block the queue behind it.
                log.warn("Chat {} could not be analysed: {}", session.id(), e.getMessage());
                repository.updateAnalysis(session.id(), ChatAnalysis.failed(clock.instant()));
            }
        }
    }

    private ChatAnalysis analyze(ChatSession session) {
        List<ChatMessage> messages = repository.findMessages(session.id());
        if (messages.stream().noneMatch(message -> message.role() == ChatRole.CLIENT)) {
            // Only the greeting: nothing was said to analyse, and nothing is spent on it.
            return ChatAnalysis.failed(clock.instant());
        }
        AiAgent agent = aiAgentUseCase.requirePublishedAgent(session.companyId(), session.aiAgentId());
        LocalDateTime chatTime = LocalDateTime.ofInstant(session.lastMessageAt(), clock.getZone());
        SummarizedCall summarized = summaryService.summarize(session.companyId(), transcriptOf(messages),
                aiAgentUseCase.resolveScenario(session.companyId(), agent).definition(), agent, chatTime);
        if (summarized == null) {
            return ChatAnalysis.failed(clock.instant());
        }
        if (!summarized.ownLlm()) {
            callBillingUseCase.chargeChatTurn(session.companyId(), session.id(), "analysis",
                    new CallUsage(0, summarized.promptTokens(), summarized.completionTokens(),
                            summarized.cachedTokens(), 0, 0));
        }
        CallSummary summary = summarized.summary();
        return new ChatAnalysis(summary.summary(), summary.sentiment(), summary.needsFollowUp(), summary.outcome(),
                clock.instant());
    }

    /** {@code ROLE: text} lines, as a call's transcript reaches the summary. */
    static String transcriptOf(List<ChatMessage> messages) {
        StringBuilder transcript = new StringBuilder();
        for (ChatMessage message : messages) {
            transcript.append(message.role() == ChatRole.CLIENT ? "CLIENT" : "AGENT")
                    .append(": ").append(message.text()).append('\n');
        }
        return transcript.toString();
    }
}
