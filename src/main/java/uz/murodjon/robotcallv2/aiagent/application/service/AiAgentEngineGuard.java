package uz.murodjon.robotcallv2.aiagent.application.service;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.realtime.RealtimeProviderRegistry;
import uz.murodjon.robotcallv2.agent.stt.SttProviderSelector;
import uz.murodjon.robotcallv2.agent.tts.TtsProviderSelector;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelUseCase;
import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel;
import uz.murodjon.robotcallv2.aimodel.domain.enums.AiModelKind;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.voice.application.port.input.TtsVoiceUseCase;
import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The speech-engine half of an agent's create and update: each engine, voice and model id
 * the form sent is one this build has and one that fits the rest of the agent. A call
 * falls back silently on an id it does not know, so an unchecked id would be a setting
 * that never takes effect.
 */
@Component
class AiAgentEngineGuard {

    private final SttProviderSelector sttProviderSelector;
    private final TtsProviderSelector ttsProviderSelector;
    private final RealtimeProviderRegistry realtimeProviderRegistry;
    private final TtsVoiceUseCase ttsVoiceUseCase;
    private final AiModelUseCase aiModelUseCase;
    private final CompanyConfigUseCase companyConfigUseCase;

    AiAgentEngineGuard(SttProviderSelector sttProviderSelector, TtsProviderSelector ttsProviderSelector,
                       RealtimeProviderRegistry realtimeProviderRegistry, TtsVoiceUseCase ttsVoiceUseCase,
                       AiModelUseCase aiModelUseCase, CompanyConfigUseCase companyConfigUseCase) {
        this.sttProviderSelector = sttProviderSelector;
        this.ttsProviderSelector = ttsProviderSelector;
        this.realtimeProviderRegistry = realtimeProviderRegistry;
        this.ttsVoiceUseCase = ttsVoiceUseCase;
        this.aiModelUseCase = aiModelUseCase;
        this.companyConfigUseCase = companyConfigUseCase;
    }

    /**
     * The recognizer this agent is pinned to — one this build has, or blank for the
     * configured default. Checked here because a call falls back silently: an id the build
     * lacks would be a setting that never takes effect.
     */
    String requireKnownSttProvider(String sttProvider) {
        String trimmed = trimmed(sttProvider);
        if (trimmed != null && !trimmed.isEmpty() && !sttProviderSelector.exists(trimmed)) {
            throw new ValidationException(ErrorCode.ENGINE_STT_PROVIDER_UNKNOWN, trimmed, sttProviderSelector.names());
        }
        return trimmed;
    }

    /** The synthesizer this agent speaks with when no voice decides it; same rule as the recognizer. */
    String requireKnownTtsProvider(String ttsProvider) {
        String trimmed = trimmed(ttsProvider);
        if (trimmed != null && !trimmed.isEmpty() && !ttsProviderSelector.exists(trimmed)) {
            throw new ValidationException(ErrorCode.ENGINE_TTS_PROVIDER_UNKNOWN, trimmed, ttsProviderSelector.names());
        }
        return trimmed;
    }

    /**
     * The speech-to-speech engine a REALTIME agent opens: one this build has, or blank when a
     * default engine is configured. A CASCADE agent keeps whatever it holds unchecked — it
     * is only read after a switch back to REALTIME, which checks it again.
     */
    String requireKnownRealtimeProvider(PipelineMode pipelineMode, String realtimeProvider) {
        String trimmed = trimmed(realtimeProvider);
        if (!pipelineMode.usesRealtimeEngine()) {
            return trimmed;
        }
        if (realtimeProviderRegistry.isEmpty()) {
            throw new ValidationException(ErrorCode.ENGINE_REALTIME_NOT_AVAILABLE);
        }
        if (trimmed == null || trimmed.isEmpty()) {
            if (realtimeProviderRegistry.findForCall(null) == null) {
                throw new ValidationException(ErrorCode.ENGINE_REALTIME_PROVIDER_REQUIRED, realtimeProviderRegistry.names());
            }
            requireToolCalling(realtimeProviderRegistry.findForCall(null).name());
            return trimmed;
        }
        if (!realtimeProviderRegistry.exists(trimmed)) {
            throw new ValidationException(ErrorCode.ENGINE_REALTIME_PROVIDER_UNKNOWN, trimmed,
                    realtimeProviderRegistry.names());
        }
        requireToolCalling(trimmed);
        return trimmed;
    }

    /** Every agent needs endCall at least, so an engine that cannot call tools cannot run one. */
    private void requireToolCalling(String realtimeProvider) {
        if (realtimeProviderRegistry.lacksToolCalling(realtimeProvider)) {
            throw new ValidationException(ErrorCode.ENGINE_REALTIME_TOOLS_UNSUPPORTED, realtimeProvider);
        }
    }

    Map<String, String> requireKnownVoicePerLanguage(long companyId, PipelineMode pipelineMode,
                                                     Map<String, String> languageVoices) {
        if (languageVoices == null || languageVoices.isEmpty()) {
            return Map.of();
        }
        Map<String, String> checked = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : languageVoices.entrySet()) {
            String language = companyConfigUseCase.resolveLanguage(companyId, entry.getKey());
            String voiceId = requireKnownVoice(pipelineMode, entry.getValue());
            if (voiceId == null) {
                continue;
            }
            TtsVoice voice = ttsVoiceUseCase.find(voiceId);
            if (voice != null && !language.equalsIgnoreCase(voice.language())) {
                throw new ValidationException(ErrorCode.TTS_VOICE_LANGUAGE_MISMATCH,
                        voiceId, voice.language(), language);
            }
            checked.put(language, voiceId);
        }
        return checked;
    }

    String requireKnownVoice(PipelineMode pipelineMode, String ttsVoice) {
        if (ttsVoice == null || ttsVoice.isBlank()) {
            return null;
        }
        String trimmed = ttsVoice.trim();
        if (!ttsVoiceUseCase.isSelectable(pipelineMode, trimmed)) {
            throw new ValidationException(ErrorCode.TTS_VOICE_UNKNOWN, trimmed,
                    ttsVoiceUseCase.findSelectableIds(pipelineMode));
        }
        return trimmed;
    }

    /**
     * The model this agent runs its turns on, or null for default.
     *
     * <p>On REALTIME the model belongs to one engine, the way an STT model belongs to one
     * recognizer: the id is handed to whichever engine the agent opens
     * ({@code RealtimeCallConfig#modelOr}), so {@code gpt-realtime-2.1} saved on a
     * Gemini Live agent is a call that cannot start. The pipeline check alone does not
     * catch it — both ids are REALTIME rows — so the pair is checked here, and only for
     * REALTIME: a cascade model has no engine to belong to.
     */
    String requireKnownModel(PipelineMode pipelineMode, String realtimeProvider, String llmModel) {
        if (llmModel == null || llmModel.isBlank()) {
            return null;
        }
        String trimmed = llmModel.trim();
        if (!aiModelUseCase.isSelectable(AiModelKind.LLM, pipelineMode, trimmed)) {
            throw new ValidationException(ErrorCode.AI_MODEL_UNKNOWN, trimmed,
                    aiModelUseCase.findSelectableIds(AiModelKind.LLM, pipelineMode));
        }
        if (pipelineMode.usesRealtimeEngine() && realtimeProvider != null && !realtimeProvider.isBlank()) {
            AiModel catalogued = aiModelUseCase.find(trimmed);
            if (catalogued != null && !catalogued.provider().equalsIgnoreCase(realtimeProvider.trim())) {
                throw new ValidationException(ErrorCode.REALTIME_MODEL_PROVIDER_MISMATCH,
                        trimmed, catalogued.provider());
            }
        }
        return trimmed;
    }

    /**
     * The cheaper text model for barge-in decisions and short answers, or null for the
     * deployment's own. It is always a cascade LLM, whatever pipeline the agent runs: a
     * realtime call that falls back runs on the cascade, and a Live model cannot answer
     * a text request — so it is checked against the cascade catalog, not the agent's mode.
     */
    String requireKnownFastModel(String fastLlmModel) {
        return requireKnownModel(PipelineMode.CASCADE, null, fastLlmModel);
    }

    /**
     * The recognition model this agent listens with, or null for its provider's own.
     *
     * <p>Checked against the same catalog rows the form was filled from, for the reason
     * {@code llm_model} is: the id is passed to the vendor verbatim, and one it does not
     * know is a call that hears nothing rather than a call that recognizes worse.
     */
    String requireKnownSttModel(String sttProvider, String sttModel) {
        return requireKnownSpeechModel(AiModelKind.STT, sttProvider, sttModel,
                ErrorCode.STT_MODEL_UNKNOWN, ErrorCode.STT_MODEL_PROVIDER_MISMATCH);
    }

    /** The synthesis model this agent speaks with, or null for its provider's own. */
    String requireKnownTtsModel(String ttsProvider, String ttsModel) {
        return requireKnownSpeechModel(AiModelKind.TTS, ttsProvider, ttsModel,
                ErrorCode.TTS_MODEL_UNKNOWN, ErrorCode.TTS_MODEL_PROVIDER_MISMATCH);
    }

    /**
     * A speech model is only accepted together with the provider that owns it. The two
     * fields are saved side by side and read side by side at call time, and a pair that
     * does not match — Deepgram's {@code nova-3} on an agent listening through Yandex —
     * reaches the vendor as an id it has never heard of, which costs the call its
     * recognition rather than degrading it.
     *
     * <p>The owner is looked up in the whole catalog, not among the ids this build can
     * reach: a model whose provider has no API key here is missing from the reachable list,
     * and reporting it as unknown sent the operator looking for a model that does exist
     * instead of at the provider field they had to change.
     */
    private String requireKnownSpeechModel(AiModelKind kind, String provider, String model,
                                           ErrorCode unknown, ErrorCode mismatch) {
        if (model == null || model.isBlank()) {
            return null;
        }
        String trimmed = model.trim();
        AiModel catalogued = aiModelUseCase.find(trimmed);
        if (catalogued != null && catalogued.kind() == kind
                && (provider == null || !catalogued.provider().equalsIgnoreCase(provider.trim()))) {
            throw new ValidationException(mismatch, trimmed, catalogued.provider());
        }
        List<AiModel> selectable = aiModelUseCase.findSelectableByKindAndMode(kind, null);
        if (selectable.stream().noneMatch(candidate -> candidate.id().equalsIgnoreCase(trimmed))) {
            throw new ValidationException(unknown, trimmed,
                    selectable.stream().map(AiModel::id).toList());
        }
        return trimmed;
    }

    /** A trimmed value, or null — a blank engine name means "the configured default". */
    private static String trimmed(String value) {
        return value != null ? value.trim() : null;
    }
}
