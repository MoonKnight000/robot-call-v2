package uz.murodjon.uysotvoice.voice.service;

import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.agent.tts.TtsProviderSelector;
import uz.murodjon.uysotvoice.voice.dto.TtsVoice;
import uz.murodjon.uysotvoice.voice.repository.TtsVoiceRepository;

import java.util.List;

/**
 * The voices a campaign can be created with, for the campaign form's voice picker and
 * for the TTS routing/warm-up code in {@code agent/tts} that needs to resolve a chosen
 * voice back into a provider + provider-side name.
 */
@Service
public class TtsVoiceService {

    private final TtsVoiceRepository repository;
    private final TtsProviderSelector providerSelector;

    public TtsVoiceService(TtsVoiceRepository repository, TtsProviderSelector providerSelector) {
        this.repository = repository;
        this.providerSelector = providerSelector;
    }

    /**
     * Voices this deployment can actually be heard through: a voice whose provider has
     * no credentials configured is not offered, because {@code TtsProviderSelector} never
     * registered it and {@code TtsRouter} would fall back to the default provider's own
     * voice instead. Not restricted to the company's {@code engine_config.ttsProvider} —
     * {@code TtsRouter} routes a chosen voice straight to the provider that owns it, so a
     * campaign is free to mix voices across every enabled provider.
     *
     * @param language optional BCP-47 filter; blank or null returns every language
     * @return the voices {@code POST /api/campaigns} will actually honour
     */
    public List<TtsVoice> findSelectable(String language) {
        return repository.forLanguage(language).stream()
                .filter(v -> providerSelector.exists(v.provider()))
                .toList();
    }

    /** The voice with this id, or {@code null} for a blank or unknown id. */
    public TtsVoice find(String id) {
        return repository.find(id);
    }

    /**
     * Whether {@code id} names a voice a campaign can actually be spoken with right now —
     * known to the catalog, and its provider enabled in this build. What {@code POST
     * /api/campaigns}' {@code ttsVoice} is checked against.
     */
    public boolean isSelectable(String id) {
        TtsVoice voice = repository.find(id);
        return voice != null && providerSelector.exists(voice.provider());
    }

    /** Ids accepted by the campaign API — what an invalid choice is reported against. */
    public List<String> selectableIds() {
        return findSelectable(null).stream().map(TtsVoice::id).toList();
    }
}
