package uz.murodjon.robotcallv2.dialer.application.dto;

import uz.murodjon.robotcallv2.agent.dialog.CallContext;
import uz.murodjon.robotcallv2.campaign.domain.enums.AmbientSound;
import uz.murodjon.robotcallv2.campaign.domain.enums.VoicemailAction;

import java.util.Map;

/**
 * Correlates an originated channel back to its campaign target so
 * AriService can build the real dialog context and apply the outcome (PROJECT.md §10).
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
        Long companyId,
        boolean disclosureEnabled,
        AmbientSound ambientSound,
        boolean midCallSmsEnabled,
        String midCallSmsTemplate,
        VoicemailAction voicemailAction,
        String voicemailMessage,
        boolean dtmfInputEnabled,
        boolean emotionAdaptiveVoice,
        /**
         * The campaign's voice per call language (§2.5) — the dialog keeps it for the
         * whole call, because the language it speaks can still change when the caller
         * turns out to speak the other one.
         */
        Map<String, String> languageVoices,
        Long sipTrunkId
) {
    public OutboundCall(Long campaignId, Long targetId, Long clientId, String phone, String language,
                        String ttsVoice, CallContext context, Long scenarioId, Long companyId, boolean disclosureEnabled) {
        this(campaignId, targetId, clientId, phone, language, ttsVoice, context, scenarioId, companyId, disclosureEnabled,
                AmbientSound.OFF, false, null, VoicemailAction.HANGUP, null, false, true, Map.of(), null);
    }
}
