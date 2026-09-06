package uz.murodjon.robotcallv2.dialer.application.dto;

/**
 * A single outbound-call instruction published to RabbitMQ and consumed to originate the
 * call (PROJECT.md §5.2, §10). Serialized as JSON.
 *
 * <p>It used to carry the campaign's voice, persona, ambient sound, voicemail action and
 * per-language voice map — twenty-odd fields copied onto every queued call, which then had
 * to be kept in step across the dialer, the queue and the ARI service. Now it carries the
 * agent's id and the consumer reads the rest once, off the ARI thread, before dialling.
 *
 * @param aiAgentId        the agent this call speaks as ({@code ai_agent})
 * @param ttsVoiceOverride an A/B variant's replacement voice, or null. The only voice that
 *                         can differ from the agent's, because a variant testing a voice is
 *                         testing exactly that one thing
 * @param promptOverride   an A/B variant's replacement for the scenario's role prompt; null
 *                         leaves it alone
 * @param variantId        the variant this call was assigned to, or null when the campaign
 *                         is not testing. Only what the outcome gets counted against
 */
public record CallTask(
        Long campaignId,
        Long targetId,
        Long clientId,
        String phone,
        String language,
        String contextData,
        long companyId,
        long aiAgentId,
        Long sipTrunkId,
        Long variantId,
        String ttsVoiceOverride,
        String promptOverride
) {
}
