package uz.murodjon.robotcallv2.voice.application.service;

import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.agent.realtime.RealtimeProviderRegistry;
import uz.murodjon.robotcallv2.agent.tts.TtsProviderSelector;
import uz.murodjon.robotcallv2.aiagent.domain.enums.PipelineMode;
import uz.murodjon.robotcallv2.voice.application.port.input.TtsVoiceUseCase;
import uz.murodjon.robotcallv2.voice.application.port.output.TtsVoiceRepository;
import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;

import java.util.List;

/**
 * The voices an agent can be created with.
 *
 * <p>A voice belongs to whichever engine speaks it, and the two families do not overlap:
 * a cascade call is spoken by a TTS vendor, a REALTIME call by the speech-to-speech
 * engine itself, and neither understands the other's voice names. So "selectable" means
 * two different things depending on who is asking — see {@link #findSelectableByMode}.
 */
@Service
public class TtsVoiceService implements TtsVoiceUseCase {

    private final TtsVoiceRepository ttsVoiceRepository;
    private final TtsProviderSelector ttsProviderSelector;
    private final RealtimeProviderRegistry realtimeProviderRegistry;

    public TtsVoiceService(TtsVoiceRepository ttsVoiceRepository,
                           TtsProviderSelector ttsProviderSelector,
                           RealtimeProviderRegistry realtimeProviderRegistry) {
        this.ttsVoiceRepository = ttsVoiceRepository;
        this.ttsProviderSelector = ttsProviderSelector;
        this.realtimeProviderRegistry = realtimeProviderRegistry;
    }

    /**
     * The voices the operator may actually pick from, narrowed to the engine the agent
     * runs on. Offering the other family would let an agent be saved with a voice its
     * engine cannot pronounce — the call would fall back to a default voice and the
     * setting would look broken rather than wrong.
     */
    @Override
    public List<TtsVoice> findSelectableByMode(PipelineMode mode, String language) {
        return ttsVoiceRepository.forLanguage(language).stream()
                .filter(voice -> spokenBy(mode, voice))
                .toList();
    }

    @Override
    public TtsVoice find(String id) {
        return ttsVoiceRepository.find(id);
    }

    @Override
    public boolean isSelectable(PipelineMode mode, String id) {
        TtsVoice voice = ttsVoiceRepository.find(id);
        return voice != null && spokenBy(mode, voice);
    }

    @Override
    public List<String> findSelectableIds(PipelineMode mode) {
        return findSelectableByMode(mode, null).stream().map(TtsVoice::id).toList();
    }

    /**
     * Whether the given engine can speak this voice. A null mode means "either engine",
     * which is what a caller with no agent in hand — the company-wide default — asks for.
     */
    private boolean spokenBy(PipelineMode mode, TtsVoice voice) {
        if (mode == PipelineMode.REALTIME) {
            return realtimeProviderRegistry.exists(voice.provider());
        }
        if (mode == PipelineMode.CASCADE) {
            return ttsProviderSelector.exists(voice.provider());
        }
        return realtimeProviderRegistry.exists(voice.provider())
                || ttsProviderSelector.exists(voice.provider());
    }
}
