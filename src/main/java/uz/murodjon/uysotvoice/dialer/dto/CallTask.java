package uz.murodjon.uysotvoice.dialer.dto;

import uz.murodjon.uysotvoice.campaign.enums.AmbientSound;
import uz.murodjon.uysotvoice.campaign.enums.VoicemailAction;

/**
 * A single outbound-call instruction published to RabbitMQ and consumed to originate
 * the call (PROJECT.md §5.2, §10). Serialized as JSON.
 *
 * @param campaignId  owning campaign
 * @param targetId    campaign_target being dialed
 * @param clientId    CRM client id (for the CRM note write-back)
 * @param phone       number to dial through the trunk
 * @param language    BCP-47 conversation language (null → campaign default)
 * @param ttsVoice    catalog id of the campaign's chosen voice (§2.5); null → the
 *                    configured routing
 * @param contextData raw {@code context_data} JSON with the debtor facts
 * @param scenarioId  the campaign's bound scenario row (ROADMAP A.3)
 * @param companyId   the campaign's owning company (ROADMAP B.1) — decides which SIP
 *                    trunk originates this call (ROADMAP B.3)
 * @param disclosureEnabled whether this campaign's calls open with the §11.1
 *                    disclosure
 * @param ambientSound ambient soundscape (OFF, OFFICE, CALL_CENTER, NATURAL_LINE, CAFE)
 * @param midCallSmsEnabled whether mid-call SMS sending is enabled
 * @param midCallSmsTemplate template for mid-call SMS messages
 * @param voicemailAction action on AMD detection (HANGUP, LEAVE_MESSAGE, IGNORE)
 * @param voicemailMessage message to speak if voicemailAction is LEAVE_MESSAGE
 * @param dtmfInputEnabled whether keypad DTMF inputs (0-9) are forwarded to dialog engine
 * @param emotionAdaptiveVoice whether voice adapts softer tone/speed upon customer frustration
 */
public record CallTask(
        Long campaignId,
        Long targetId,
        Long clientId,
        String phone,
        String language,
        String ttsVoice,
        String contextData,
        Long scenarioId,
        long companyId,
        boolean disclosureEnabled,
        AmbientSound ambientSound,
        boolean midCallSmsEnabled,
        String midCallSmsTemplate,
        VoicemailAction voicemailAction,
        String voicemailMessage,
        boolean dtmfInputEnabled,
        boolean emotionAdaptiveVoice
) {
    public CallTask(Long campaignId, Long targetId, Long clientId, String phone, String language,
                    String ttsVoice, String contextData, Long scenarioId, long companyId, boolean disclosureEnabled) {
        this(campaignId, targetId, clientId, phone, language, ttsVoice, contextData, scenarioId, companyId,
                disclosureEnabled, AmbientSound.OFF, false, null, VoicemailAction.HANGUP, null, false, true);
    }
}
