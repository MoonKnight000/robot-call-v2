package uz.murodjon.robotcallv2.copilot.domain.entity;

/**
 * What the model answered to one question, after every tool round it took.
 *
 * @param promptTokens     summed over the rounds
 * @param completionTokens summed over the rounds
 */
public record CopilotModelReply(String text, long promptTokens, long completionTokens) {
}
