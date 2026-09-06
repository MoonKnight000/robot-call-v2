package uz.murodjon.robotcallv2.voice.application.service;

import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.agent.realtime.RealtimeProviderRegistry;
import uz.murodjon.robotcallv2.agent.tts.TtsProviderSelector;
import uz.murodjon.robotcallv2.engine.application.service.EngineConfigService;
import uz.murodjon.robotcallv2.engine.domain.enums.PipelineMode;
import uz.murodjon.robotcallv2.voice.application.port.input.TtsVoiceUseCase;
import uz.murodjon.robotcallv2.voice.application.port.output.TtsVoiceRepository;
import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;

import java.util.List;

/**
 * The voices a campaign can be created with.
 *
 * <p>A voice belongs to whichever engine speaks it, and the two families do not overlap:
 * a cascade call is spoken by a TTS vendor, a REALTIME call by the speech-to-speech
 * engine itself, and neither understands the other's voice names. So "selectable" means
 * two different things depending on who is asking — see
 * {@link #findSelectableByCompanyId}.
 */
@Service
public class TtsVoiceService implements TtsVoiceUseCase {

    private final TtsVoiceRepository ttsVoiceRepository;
    private final TtsProviderSelector ttsProviderSelector;
    private final RealtimeProviderRegistry realtimeProviderRegistry;
    private final EngineConfigService engineConfigService;

    public TtsVoiceService(TtsVoiceRepository ttsVoiceRepository, TtsProviderSelector ttsProviderSelector,
                           RealtimeProviderRegistry realtimeProviderRegistry,
                           EngineConfigService engineConfigService) {
        this.ttsVoiceRepository = ttsVoiceRepository;
        this.ttsProviderSelector = ttsProviderSelector;
        this.realtimeProviderRegistry = realtimeProviderRegistry;
        this.engineConfigService = engineConfigService;
    }

    /**
     * Voices a TTS vendor can synthesize, with no company to narrow by. This is the set
     * the warm-up sweep pre-renders, which is why realtime voices are deliberately absent:
     * they are never synthesized by anything, and warming them would spend a TTS vendor's
     * quota rendering phrases under a voice that vendor does not have.
     */
    @Override
    public List<TtsVoice> findSelectable(String language) {
        return ttsVoiceRepository.forLanguage(language).stream()
                .filter(v -> ttsProviderSelector.exists(v.provider()))
                .toList();
    }

    /**
     * The voices the operator may actually pick from, narrowed to the engine their company
     * runs on. Offering the other family would let a campaign be saved with a voice the
     * engine cannot pronounce — the call would fall back to a default voice and the
     * setting would look broken rather than wrong.
     */
    @Override
    public List<TtsVoice> findSelectableByCompanyId(long companyId, String language) {
        return ttsVoiceRepository.forLanguage(language).stream()
                .filter(voice -> ownedByEngineOf(companyId, voice))
                .toList();
    }

    @Override
    public TtsVoice find(String id) {
        return ttsVoiceRepository.find(id);
    }

    /**
     * Whether the company may save a campaign with this voice — engine-aware for the same
     * reason {@link #findSelectableByCompanyId} is, so validation accepts exactly what the
     * form offered.
     */
    @Override
    public boolean isSelectable(long companyId, String id) {
        TtsVoice voice = ttsVoiceRepository.find(id);
        return voice != null && ownedByEngineOf(companyId, voice);
    }

    @Override
    public List<String> findSelectableIds(long companyId) {
        return findSelectableByCompanyId(companyId, null).stream().map(TtsVoice::id).toList();
    }

    private boolean ownedByEngineOf(long companyId, TtsVoice voice) {
        boolean realtime = engineConfigService.findEffectiveByCompanyId(companyId).mode()
                == PipelineMode.REALTIME;
        return realtime
                ? realtimeProviderRegistry.exists(voice.provider())
                : ttsProviderSelector.exists(voice.provider());
    }
}
