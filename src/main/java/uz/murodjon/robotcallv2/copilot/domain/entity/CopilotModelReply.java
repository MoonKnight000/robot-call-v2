package uz.murodjon.robotcallv2.copilot.domain.entity;

/**
 * What the model answered to one question, after every tool round it took.
 *
 * @param promptTokens     summed over the rounds
 * @param completionTokens summed over the rounds
 * @param model            the model id the provider reported (or the configured one); null when unknown —
 *                         billing then prices the tokens at the common rate
 */
public record CopilotModelReply(String text, long promptTokens, long completionTokens, String model) {

    /** A reply whose model is not known. */
    public CopilotModelReply(String text, long promptTokens, long completionTokens) {
        this(text, promptTokens, completionTokens, null);
    }
}
