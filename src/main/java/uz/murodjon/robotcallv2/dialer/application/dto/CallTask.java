package uz.murodjon.robotcallv2.dialer.application.dto;

import uz.murodjon.robotcallv2.campaign.domain.enums.AmbientSound;
import uz.murodjon.robotcallv2.campaign.domain.enums.VoicemailAction;

import java.util.Map;

/**
 * A single outbound-call instruction published to RabbitMQ and consumed to originate
 * the call (PROJECT.md §5.2, §10). Serialized as JSON.
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
        boolean emotionAdaptiveVoice,
        /**
         * The campaign's voice per call language (§2.5). Carried whole rather than
         * resolved here because the language can still change twice: the CRM's preferred
         * language is only read when the task is consumed, and the caller's own language
         * is only heard once the call is up.
         */
        Map<String, String> languageVoices,
        Long sipTrunkId
) {
    public CallTask(Long campaignId, Long targetId, Long clientId, String phone, String language,
                    String ttsVoice, String contextData, Long scenarioId, long companyId, boolean disclosureEnabled) {
        this(campaignId, targetId, clientId, phone, language, ttsVoice, contextData, scenarioId, companyId,
                disclosureEnabled, AmbientSound.OFF, false, null, VoicemailAction.HANGUP, null, false, true,
                Map.of(), null);
    }

    /** The voice this task's campaign speaks {@code language} with; its default otherwise. */
    public String voiceFor(String language) {
        if (language != null && languageVoices != null) {
            String voice = languageVoices.get(language);
            if (voice != null && !voice.isBlank()) {
                return voice;
            }
        }
        return ttsVoice;
    }
}
