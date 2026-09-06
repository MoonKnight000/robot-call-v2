package uz.murodjon.robotcallv2.dialer.application.dto;

import uz.murodjon.robotcallv2.agent.dialog.CallContext;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;

/**
 * Correlates an originated channel back to its campaign target so AriService can build the
 * real dialog context and apply the outcome (PROJECT.md §10).
 *
 * <p>Carries the {@link AiAgent} itself rather than a copy of its settings: it is resolved
 * once in {@code CallTaskConsumer}, before the number is dialled, and everything the call
 * needs to know about how it should sound is then one field away — on the ARI thread,
 * where a database read would sit between the answer and the first word.
 *
 * @param ttsVoice the voice this call actually speaks with: the A/B variant's if it named
 *                 one, otherwise the agent's for {@code language}
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
        /** The A/B variant this call runs, or null when the campaign is not testing. */
        Long variantId,
        /** The variant's replacement for the scenario's role prompt; null leaves it alone. */
        String promptOverride
) {
}
