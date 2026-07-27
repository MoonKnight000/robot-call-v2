package uz.murodjon.uysotvoice.dialer;

import uz.murodjon.uysotvoice.agent.dialog.CallContext;

/**
 * Correlates an originated channel back to its campaign target so
 * {@code AriService} can build the real dialog context and apply the outcome
 * (PROJECT.md §10). Held in {@link OutboundCallRegistry} keyed by channel id.
 *
 * @param campaignId owning campaign
 * @param targetId   campaign_target id (for status/retry updates)
 * @param clientId   CRM client id (for the CRM note)
 * @param phone      dialled number — needed to record a phone-level opt-out (§11.4)
 * @param language   conversation language for this call
 * @param ttsVoice   catalog id of the voice this call speaks with (§2.5); null → the
 *                   configured routing
 * @param context    debtor facts for the system prompt
 */
public record OutboundCall(
        Long campaignId,
        Long targetId,
        Long clientId,
        String phone,
        String language,
        String ttsVoice,
        CallContext context
) {
}
