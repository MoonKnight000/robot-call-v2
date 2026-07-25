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
 * @param language   conversation language for this call
 * @param context    debtor facts for the system prompt
 */
public record OutboundCall(
        Long campaignId,
        Long targetId,
        Long clientId,
        String language,
        CallContext context
) {
}
