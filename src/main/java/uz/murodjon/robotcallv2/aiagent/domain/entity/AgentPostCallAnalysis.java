package uz.murodjon.robotcallv2.aiagent.domain.entity;

/**
 * Which of the fixed parts of the post-call analysis an agent keeps, and how it is made. The call
 * is still analysed once — its outcome, follow-up and scores drive the dialer — so a switch only
 * drops the part from what is stored and shown.
 *
 * @param summaryEnabled   keep the written summary of the call
 * @param sentimentEnabled keep the caller's sentiment
 * @param model            LLM model id the call is analysed with; {@code null} — the platform's
 *                         summary model. A company on its own LLM is always analysed there
 * @param memoryEnabled    the call updates the client's cross-call memory (Retell's "Save
 *                         conversation to memory"). Never null: a request that leaves it out —
 *                         an older client — keeps memory on, as it was before the switch existed
 */
public record AgentPostCallAnalysis(boolean summaryEnabled, boolean sentimentEnabled, String model,
                                    Boolean memoryEnabled) {

    public static final AgentPostCallAnalysis ALL = new AgentPostCallAnalysis(true, true, null, true);

    public AgentPostCallAnalysis {
        model = model == null || model.isBlank() ? null : model.trim();
        memoryEnabled = memoryEnabled == null || memoryEnabled;
    }
}
