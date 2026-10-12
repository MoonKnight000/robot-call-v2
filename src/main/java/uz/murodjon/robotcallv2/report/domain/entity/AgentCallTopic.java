package uz.murodjon.robotcallv2.report.domain.entity;

/**
 * One subject AI-agent calls were about over a period, from the tags the post-call summary
 * writes ({@code call_result.outcome.topics}) — what customers actually call for.
 *
 * @param topic          the tag, lowercased
 * @param calls          calls tagged with it
 * @param convertedCalls of those, calls that ended in a conversion ({@code Disposition#isConversion})
 * @param escalatedCalls of those, calls handed to a person
 */
public record AgentCallTopic(String topic, long calls, long convertedCalls, long escalatedCalls) {
}
