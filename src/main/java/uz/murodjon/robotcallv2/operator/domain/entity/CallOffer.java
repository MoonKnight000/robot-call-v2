package uz.murodjon.robotcallv2.operator.domain.entity;

/**
 * The screen pop an operator gets while their phone rings (SSE {@code CALL_OFFER}): who is
 * calling, from which queue, what they chose and what the AI already heard — so nobody
 * has to ask the caller to start over.
 *
 * @param channelId     the caller's channel — what hold, transfer and monitor act on
 * @param callAttemptId the call row; its transcript and CRM card are one request away
 * @param summary       the same handover text the operator hears in the whisper
 * @param transcript    what was said with the AI agent, if one talked first
 */
public record CallOffer(
        String channelId,
        long callAttemptId,
        String callerNumber,
        String clientName,
        String queueName,
        int waitSeconds,
        String language,
        boolean vip,
        String summary,
        String transcript
) {
}
