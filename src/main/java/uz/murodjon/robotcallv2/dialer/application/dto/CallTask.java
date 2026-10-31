package uz.murodjon.robotcallv2.dialer.application.dto;

import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;

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
 * @param pipelineModeOverride an A/B variant's pipeline for this call, or null for the
 *                         agent's own — the cascade-versus-realtime test
 * @param callerIdPoolId   the campaign's caller ID pool, or null to present the trunk's own number
 * @param callerId         the pool number this call presents, chosen by the dialer together with
 *                         {@code sipTrunkId} — the number's own trunk; null when there is no pool
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
        String promptOverride,
        PipelineMode pipelineModeOverride,
        Long callerIdPoolId,
        String callerId
) {

    /** A task that names no caller ID pool: the trunk's own caller ID is presented. */
    public CallTask(Long campaignId, Long targetId, Long clientId, String phone, String language,
                    String contextData, long companyId, long aiAgentId, Long sipTrunkId, Long variantId,
                    String ttsVoiceOverride, String promptOverride, PipelineMode pipelineModeOverride) {
        this(campaignId, targetId, clientId, phone, language, contextData, companyId, aiAgentId, sipTrunkId,
                variantId, ttsVoiceOverride, promptOverride, pipelineModeOverride, null, null);
    }

    /** The RabbitMQ queue a call task travels on, from the dialer's sweep to CallTaskConsumer. */
    public static final String QUEUE = "call.tasks";
}
