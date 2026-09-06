package uz.murodjon.robotcallv2.aiagent.domain.entity;

import uz.murodjon.robotcallv2.aiagent.domain.enums.AmbientSound;
import uz.murodjon.robotcallv2.aiagent.domain.enums.VoicemailAction;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Who speaks a call, as opposed to what is said ({@code Scenario}) and who is called
 * ({@code Campaign}).
 *
 * <p>Every call in the system resolves through exactly one of these, in either direction:
 * a campaign names the agent its targets are dialled by, an inbound route names the agent
 * its DID is answered by. That is the whole point of the type — before it, an outbound
 * call read its voice and persona off the campaign and an inbound call off the scenario,
 * so the same script answered the phone as a different character than it called with.
 *
 * <p>The three model fields are overrides and may be null; null means the company's
 * {@code ai_model_config}. Everything else is decided here: a value nobody set is this
 * record's own default, not somebody else's.
 *
 * @param scenarioId     the script this agent runs. One scenario can be spoken by several
 *                       agents — an Uzbek one and a Russian one, a careful collections
 *                       agent and a cheap survey agent — which is why it is a reference
 *                       and not a copy
 * @param language       the language a call starts in when the target does not name one
 * @param ttsVoice       voice for that language, and for any language {@code languageVoices}
 *                       does not name
 * @param languageVoices voice per call language ({@code {"uz-UZ": "nigora", "ru-RU": "alena"}});
 *                       the language the caller turns out to speak switches it mid-call
 * @param sipTrunkIds    trunks this agent may dial out from; empty balances across every
 *                       enabled trunk of the company
 */
public record AiAgent(
        long id,
        long companyId,
        String name,
        String description,
        long scenarioId,
        String language,
        String ttsVoice,
        Map<String, String> languageVoices,
        AgentPersona persona,
        String llmModel,
        Double temperature,
        Integer maxOutputTokens,
        AmbientSound ambientSound,
        boolean emotionAdaptiveVoice,
        boolean dtmfInputEnabled,
        VoicemailAction voicemailAction,
        String voicemailMessage,
        boolean midCallSmsEnabled,
        String midCallSmsTemplate,
        Set<Long> sipTrunkIds,
        boolean enabled,
        Instant createdAt,
        Long createdBy
) {

    /** The voice a call in {@code language} speaks with; this agent's default otherwise. */
    public String voiceFor(String language) {
        if (language != null && languageVoices != null) {
            String voice = languageVoices.get(language);
            if (voice != null && !voice.isBlank()) {
                return voice;
            }
        }
        return ttsVoice;
    }

    public AgentPersona personaOrDefault() {
        return persona != null ? persona : AgentPersona.AI_ASSISTANT;
    }

    /**
     * Whether calls by this agent open with the §11.1 disclosure. An agent that introduces
     * itself as a person has already answered that question the other way.
     */
    public boolean disclosureEnabled() {
        return personaOrDefault() == AgentPersona.AI_ASSISTANT;
    }

    public Set<Long> sipTrunkIdsOrEmpty() {
        return sipTrunkIds == null ? Set.of() : sipTrunkIds;
    }

    public Map<String, String> languageVoicesOrEmpty() {
        return languageVoices == null ? Map.of() : languageVoices;
    }
}
