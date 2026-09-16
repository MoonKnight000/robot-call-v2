package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;
import uz.murodjon.robotcallv2.shared.dialog.DialogPhrases;
import uz.murodjon.robotcallv2.shared.dialog.Disclosure;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collection;
import java.util.List;

/**
 * The rules both pipelines apply to a call whoever does the talking — which persona may
 * speak, which disclosure wording is used, when a limit closes the call, which figures the
 * fact guard refuses, and how a closing call leaves the line.
 *
 * <p>Kept in one place because the cascade and realtime engines used to carry their own
 * copies, and a rule enforced in two places is a rule that eventually differs. What each
 * engine does <em>about</em> a rule stays with the engine: the cascade pipeline withholds a
 * refused figure before it is spoken, the realtime one audits it after.
 */
@Component
class DialogPolicy {

    private final DialogProperties dialogProperties;
    private final DialogTuningProperties dialogTuning;

    DialogPolicy(DialogProperties dialogProperties, DialogTuningProperties dialogTuning) {
        this.dialogProperties = dialogProperties;
        this.dialogTuning = dialogTuning;
    }

    /**
     * Who this call's agent may present itself as. A company that has not been allowed a
     * human-like persona gets the disclosure whatever its agent was saved with
     * ({@code CompanyConfig#humanLikePersonaAllowed}); a call with no agent is an AI assistant.
     */
    static AgentPersona resolvePersona(AiAgent agent, CompanyConfig companyConfig) {
        boolean humanLikeAllowed = companyConfig == null || companyConfig.humanLikePersonaAllowed();
        return agent != null && (humanLikeAllowed || agent.persona() != AgentPersona.HUMAN_LIKE)
                ? agent.persona() : AgentPersona.AI_ASSISTANT;
    }

    /**
     * The §11.1 disclosure this call opens with, most specific wording first: the
     * scenario's own, then the calling company's, then the platform's. Each configured
     * text has to survive {@link Disclosure} to be used at all — that is what keeps the
     * notice from being worded away, whichever level tries it.
     */
    static String resolveDisclosureLine(ScenarioDefinition scenario, String companyText,
                                        String language, String companyName) {
        String scenarioLine = Disclosure.resolve(
                scenario != null ? scenario.disclosureText() : null, language, companyName);
        if (scenarioLine != null) {
            return scenarioLine;
        }
        String companyLine = Disclosure.resolve(companyText, language, companyName);
        return companyLine != null ? companyLine : DialogPhrases.disclosure(language, companyName);
    }

    /** Whether a call has spent its token budget (§C14); a cap of 0 or less is no budget. */
    static boolean isTokenBudgetSpent(long cap, long used) {
        return cap > 0 && used >= cap;
    }

    /**
     * Whether the turn just started is past the call's turn cap (§4.4). A cap of 0 or less
     * means no cap, as on the realtime pipeline.
     *
     * @param turn the number of the turn just started, counting from 1
     */
    static boolean isTurnCapExceeded(int turn, int maxTurns) {
        return maxTurns > 0 && turn > maxTurns;
    }

    /**
     * The seconds after which the call is closed ({@link CallLimits#maxCallSeconds}), with
     * the deployment's {@code max-call-seconds} as the fallback; 0 means no limit.
     */
    int resolveMaxCallSeconds(Integer companyCapSeconds, AiAgent agent) {
        return CallLimits.maxCallSeconds(companyCapSeconds, agent, dialogProperties.maxCallSeconds());
    }

    /**
     * Money figures in {@code text} that are neither in the call's facts nor in what its
     * company tools answered with (§4.4); empty when the guard is off.
     */
    List<String> findFactViolations(String text, ScenarioDefinition scenario, CallContext context,
                                    Collection<BigDecimal> toolFigures) {
        if (!dialogProperties.factGuard()) {
            return List.of();
        }
        return FactGuard.violations(text, scenario, context, toolFigures);
    }

    /** How a closing call leaves the line: to a human operator when it was transferred (§11.6), otherwise hung up. */
    static Runnable selectClosingAction(Disposition disposition, Runnable transfer, Runnable hangup) {
        return (disposition == Disposition.TRANSFERRED && transfer != null) ? transfer : hangup;
    }

    /**
     * Wait for the queued audio to finish playing, then for the tail to reach the caller.
     *
     * <p>Polls the endpoint rather than sleeping for a precomputed duration: with sentence
     * streaming the total length is not known when the last sentence is queued, and
     * hanging up early would cut off the goodbye. Gives up after {@code drain-timeout-ms}.
     *
     * @return false when the wait was interrupted (the interrupt flag is set again)
     */
    boolean awaitPlaybackDrained(RtpEndpoint endpoint) {
        long deadline = System.nanoTime() + Duration.ofMillis(dialogTuning.drainTimeoutMs()).toNanos();
        try {
            while (endpoint.isPlaying() && System.nanoTime() < deadline) {
                Thread.sleep(dialogTuning.playbackPollMs());
            }
            Thread.sleep(dialogTuning.hangupTailMs()); // let the tail reach the caller before the channel drops
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
