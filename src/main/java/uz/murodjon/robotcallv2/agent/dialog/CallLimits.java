package uz.murodjon.robotcallv2.agent.dialog;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;

/**
 * The one rule for how long a call may run, whichever pipeline runs it.
 *
 * <p>Two settings meet here: the company's cap ({@code AiModelConfig.maxCallSeconds}, a
 * spend limit the agent cannot raise) and the agent's own
 * {@code maxConversationDurationSeconds}. The call gets the tighter of the two. Before
 * this the cascade pipeline read only the company's number and the realtime pipeline only
 * the agent's, so the same agent had two different ceilings depending on its engine.
 */
public final class CallLimits {

    private CallLimits() {
    }

    /**
     * @param companyCapSeconds the company's cap; {@code null} or {@code 0} when none is set
     * @param agent             the agent on the call, or null for a call with no agent
     * @param fallbackSeconds   the deployment default when neither says anything
     * @return the seconds after which the call is closed; {@code 0} means no limit
     */
    public static int maxCallSeconds(Integer companyCapSeconds, AiAgent agent, int fallbackSeconds) {
        int cap = companyCapSeconds != null && companyCapSeconds > 0 ? companyCapSeconds : fallbackSeconds;
        Integer own = agent != null && agent.limits() != null ? agent.limits().maxConversationDurationSeconds() : null;
        if (own == null || own <= 0) {
            return cap;
        }
        return cap > 0 ? Math.min(own, cap) : own;
    }

    /**
     * The agent's own silence ceiling ({@code silenceEndCallTimeoutSeconds}): total quiet after
     * which the call is ended whatever the reminder prompts did.
     *
     * @return the seconds, or {@code 0} when the agent sets none
     */
    public static int silenceEndCallSeconds(AiAgent agent) {
        Integer own = agent != null && agent.limits() != null ? agent.limits().silenceEndCallTimeoutSeconds() : null;
        return own != null && own > 0 ? own : 0;
    }

    /**
     * How long the caller may be silent before the agent asks whether they are still there.
     *
     * @param agent           the agent on the call, or null
     * @param fallbackSeconds the deployment default when the agent sets none
     * @return the seconds of idle silence
     */
    public static int reminderSeconds(AiAgent agent, int fallbackSeconds) {
        Integer own = agent != null && agent.callBehaviour() != null ? agent.callBehaviour().reminderAfterSeconds() : null;
        return own != null && own > 0 ? own : fallbackSeconds;
    }

    /**
     * How many reminder prompts to speak before giving up on the caller and ending the call.
     *
     * @param agent         the agent on the call, or null
     * @param fallbackCount the deployment default when the agent sets none
     * @return the maximum prompts; 0 means end at the first silence without asking
     */
    public static int reminderMaxCount(AiAgent agent, int fallbackCount) {
        Integer own = agent != null && agent.callBehaviour() != null ? agent.callBehaviour().reminderMaxCount() : null;
        return own != null && own >= 0 ? own : fallbackCount;
    }

    /**
     * {@code maxCallSeconds} tightened to what the company's balance still pays for.
     *
     * @param affordableSeconds {@code CallBillingUseCase.findAffordableCallSeconds}; {@code 0}
     *                          when the balance is not enforced
     */
    public static int capByBalance(int maxCallSeconds, int affordableSeconds) {
        if (affordableSeconds <= 0) {
            return maxCallSeconds;
        }
        return maxCallSeconds > 0 ? Math.min(maxCallSeconds, affordableSeconds) : affordableSeconds;
    }
}
