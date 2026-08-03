package uz.murodjon.uysotvoice.dialer.dto;

import uz.murodjon.uysotvoice.agent.dialog.CallContext;
import uz.murodjon.uysotvoice.dialer.service.OutboundCallRegistry;

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
 * @param scenarioId the campaign's bound scenario row (ROADMAP A.3)
 * @param disclosureEnabled whether this campaign's calls open with the §11.1
 *                   disclosure
 */
public record OutboundCall(
        Long campaignId,
        Long targetId,
        Long clientId,
        String phone,
        String language,
        String ttsVoice,
        CallContext context,
        Long scenarioId,
        boolean disclosureEnabled
) {
}
