package uz.murodjon.robotcallv2.dialer.application.dto;

import uz.murodjon.robotcallv2.agent.dialog.CallContext;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;

/**
 * Correlates an originated channel back to its campaign target so CallSetupService can build the
 * real dialog context and apply the outcome (PROJECT.md §10).
 *
 * <p>Carries the {@link AiAgent} itself rather than a copy of its settings: it is resolved
 * once in {@code CallTaskConsumer}, before the number is dialled, and everything the call
 * needs to know about how it should sound is then one field away — on the ARI thread,
 * where a database read would sit between the answer and the first word.
 *
 * @param ttsVoice             the voice this call actually speaks with: the A/B variant's if
 *                             it named one, otherwise the agent's for {@code language}
 * @param variantId            the A/B variant this call runs, or null when the campaign is
 *                             not testing
 * @param promptOverride       the variant's replacement for the scenario's role prompt; null
 *                             leaves it alone
 * @param pipelineModeOverride the variant's pipeline for this call; null runs the agent's own
 */
public record OutboundCall(
        Long campaignId,
        Long targetId,
        Long clientId,
        String phone,
        String language,
        String ttsVoice,
        CallContext context,
        AiAgent agent,
        Long companyId,
        Long sipTrunkId,
        Long variantId,
        String promptOverride,
        PipelineMode pipelineModeOverride,
        Long callerIdPoolId
) {

    public OutboundCall(Long campaignId, Long targetId, Long clientId, String phone, String language,
                        String ttsVoice, CallContext context, AiAgent agent, Long companyId,
                        Long sipTrunkId, Long variantId, String promptOverride,
                        PipelineMode pipelineModeOverride) {
        this(campaignId, targetId, clientId, phone, language, ttsVoice, context, agent,
                companyId, sipTrunkId, variantId, promptOverride, pipelineModeOverride, null);
    }
}
