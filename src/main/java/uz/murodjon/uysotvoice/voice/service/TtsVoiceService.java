package uz.murodjon.uysotvoice.voice.service;

import org.springframework.stereotype.Service;

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

    public TtsVoiceService(TtsVoiceRepository repository) {
        this.repository = repository;
    }

    /**
     * @param language optional BCP-47 filter; blank or null returns the whole catalog
     * @return the voices {@code POST /api/campaigns} will accept
     */
    public List<TtsVoice> voices(String language) {
        return repository.forLanguage(language);
    }

    /** Every selectable voice. */
    public List<TtsVoice> all() {
        return repository.all();
    }

    /** The voice with this id, or {@code null} for a blank or unknown id. */
    public TtsVoice find(String id) {
        return repository.find(id);
    }

    /** Ids accepted by the campaign API — what an invalid choice is reported against. */
    public List<String> ids() {
        return repository.ids();
    }
}
