package uz.murodjon.uysotvoice.dialer.dto;

import uz.murodjon.uysotvoice.agent.dialog.CallContext;
import uz.murodjon.uysotvoice.campaign.enums.AmbientSound;
import uz.murodjon.uysotvoice.campaign.enums.VoicemailAction;
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
 * @param ambientSound ambient soundscape (OFF, OFFICE, CALL_CENTER, NATURAL_LINE, CAFE)
 * @param midCallSmsEnabled whether mid-call SMS sending is enabled
 * @param midCallSmsTemplate template for mid-call SMS messages
 * @param voicemailAction action on AMD detection (HANGUP, LEAVE_MESSAGE, IGNORE)
 * @param voicemailMessage message to speak if voicemailAction is LEAVE_MESSAGE
 * @param dtmfInputEnabled whether keypad DTMF inputs (0-9) are forwarded to dialog engine
 * @param emotionAdaptiveVoice whether voice adapts softer tone/speed upon customer frustration
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
        boolean disclosureEnabled,
        AmbientSound ambientSound,
        boolean midCallSmsEnabled,
        String midCallSmsTemplate,
        VoicemailAction voicemailAction,
        String voicemailMessage,
        boolean dtmfInputEnabled,
        boolean emotionAdaptiveVoice
) {
    public OutboundCall(Long campaignId, Long targetId, Long clientId, String phone, String language,
                        String ttsVoice, CallContext context, Long scenarioId, boolean disclosureEnabled) {
        this(campaignId, targetId, clientId, phone, language, ttsVoice, context, scenarioId, disclosureEnabled,
                AmbientSound.OFF, false, null, VoicemailAction.HANGUP, null, false, true);
    }
}
