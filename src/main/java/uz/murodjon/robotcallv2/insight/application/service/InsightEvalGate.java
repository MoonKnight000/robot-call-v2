package uz.murodjon.robotcallv2.insight.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.event.AgentEvalFinishedEvent;
import uz.murodjon.robotcallv2.insight.application.port.output.AgentInsightRepository;
import uz.murodjon.robotcallv2.insight.domain.entity.AgentInsight;
import uz.murodjon.robotcallv2.insight.domain.enums.InsightStatus;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;

import java.time.Clock;

/**
 * The gate (UNIVERSAL-OPERATOR-PLAN D.4): an accepted prompt change stays only if the agent
 * still passes every one of its own test cases with it.
 *
 * <p>Every case, not most of them. The suggestion came from a model reading a day of flagged
 * calls, and the test cases are the things a person already decided the agent must get right;
 * a change that trades one of those for a fix is not an improvement, it is a different bug.
 *
 * <p>A failed run is undone by removing the one sentence that was added, which is why the
 * suggestion is a sentence rather than a rewritten prompt: whatever else the operator edited
 * in the meantime survives the rollback.
 *
 * <p>Passing is not publishing. It notifies, and a person presses publish.
 */
@Component
class InsightEvalGate {

    private static final Logger log = LoggerFactory.getLogger(InsightEvalGate.class);

    private final AgentInsightRepository repository;
    private final AiAgentUseCase aiAgentUseCase;
    private final NotificationUseCase notificationUseCase;
    private final Clock clock;

    InsightEvalGate(AgentInsightRepository repository, AiAgentUseCase aiAgentUseCase,
                    NotificationUseCase notificationUseCase, Clock clock) {
        this.repository = repository;
        this.aiAgentUseCase = aiAgentUseCase;
        this.notificationUseCase = notificationUseCase;
        this.clock = clock;
    }

    @EventListener
    public void settle(AgentEvalFinishedEvent event) {
        AgentInsight insight = repository.findByEvalRunId(event.runId());
        // Every other evaluation — the editor's own "run the tests" button — ends here.
        if (insight == null || insight.status() != InsightStatus.EVALUATING) {
            return;
        }
        String verdict = "eval " + event.passed() + "/" + event.total();
        if (event.allPassed()) {
            repository.save(insight.accepted(verdict, insight.decidedBy(), clock.instant()));
            notificationUseCase.notify(insight.companyId(), NotificationType.AGENT_INSIGHT_READY,
                    "Agent yaxshilanishi tayyor",
                    insight.title() + " — test holatlari o'tdi (" + verdict + "). Qoralamani chop etasizmi?",
                    "/ai-agents/" + insight.aiAgentId());
            log.info("[company {}] Insight {} passed the gate ({})", insight.companyId(), insight.id(), verdict);
            return;
        }
        repository.save(insight.rejected(verdict + " — " + rollback(insight), insight.decidedBy(), clock.instant()));
        log.info("[company {}] Insight {} failed the gate ({})", insight.companyId(), insight.id(), verdict);
    }

    /**
     * Takes the sentence back out of the draft, and says so in the words the inbox will show.
     *
     * <p>It can fail — somebody saved the agent between the accept and now, and the draft has
     * moved on. The proposal is still rejected: leaving it {@code EVALUATING} would leave a
     * row waiting for a run that has already finished. What the reason says changes instead,
     * because then the sentence is still in the draft and a person has to take it out.
     */
    private String rollback(AgentInsight insight) {
        try {
            aiAgentUseCase.changeScenarioGuardrail(insight.companyId(), insight.aiAgentId(),
                    insight.suggestion(), false);
            return "o'zgarish qoralamadan qaytarildi";
        } catch (RuntimeException e) {
            log.error("[company {}] Insight {} failed the gate, but its rule could not be removed from agent {}: {}",
                    insight.companyId(), insight.id(), insight.aiAgentId(), e.getMessage());
            return "o'zgarishni qoralamadan qaytarib bo'lmadi, uni qo'lda o'chiring: \"" + insight.suggestion() + "\"";
        }
    }
}
