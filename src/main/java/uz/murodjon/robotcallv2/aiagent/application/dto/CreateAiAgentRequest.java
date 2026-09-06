package uz.murodjon.robotcallv2.aiagent.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import uz.murodjon.robotcallv2.aiagent.domain.enums.AmbientSound;
import uz.murodjon.robotcallv2.aiagent.domain.enums.VoicemailAction;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;

import java.util.Map;
import java.util.Set;

/**
 * A new AI agent ({@code POST /api/ai-agents}) — the voice, persona and model a scenario
 * is spoken with, and the trunks it may dial from.
 *
 * @param scenarioId     required: the script this agent runs, from {@code GET /api/scenarios}
 * @param language       BCP-47 language a call starts in; omit for the company's default
 * @param ttsVoice       id from {@code GET /api/tts/voices}; omit to speak with the
 *                       company's configured voice. An unknown id is rejected
 * @param languageVoices voice per call language ({@code {"uz-UZ": "nigora", "ru-RU": "alena"}}).
 *                       Every id must speak the language it is mapped to — anything else is a 400
 * @param persona        AI_ASSISTANT (default) opens the call with the §11.1 disclosure;
 *                       HUMAN_LIKE does not
 * @param llmModel       model id for this agent's turns — a cheaper one for a survey, a
 *                       stronger one for collections; omit for the company's setting
 * @param temperature    0..2; omit for the company's setting
 * @param maxOutputTokens per-turn cap; omit for the company's setting
 * @param sipTrunkIds    trunks this agent dials out from; omit to balance across every
 *                       enabled trunk of the company
 */
public record CreateAiAgentRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 500) String description,
        @NotNull Long scenarioId,
        String language,
        String ttsVoice,
        Map<String, String> languageVoices,
        AgentPersona persona,
        @Size(max = 120) String llmModel,
        Double temperature,
        Integer maxOutputTokens,
        AmbientSound ambientSound,
        Boolean emotionAdaptiveVoice,
        Boolean dtmfInputEnabled,
        VoicemailAction voicemailAction,
        @Size(max = 500) String voicemailMessage,
        Boolean midCallSmsEnabled,
        @Size(max = 500) String midCallSmsTemplate,
        Set<Long> sipTrunkIds,
        Boolean enabled) {
}
