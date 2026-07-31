package uz.murodjon.uysotvoice.voice.service;

import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.agent.tts.TtsVoice;
import uz.murodjon.uysotvoice.agent.tts.TtsVoiceCatalog;

import java.util.List;

/**
 * The voices a campaign can be created with, for the campaign form's voice picker.
 *
 * <p>A thin read over {@link TtsVoiceCatalog}, which lives in {@code agent/tts} because
 * the synthesis router needs it on every turn. This is the API's side of that catalog, so
 * the controller depends on its own package rather than on the voice pipeline.
 */
@Service
public class TtsVoiceService {

    private final TtsVoiceCatalog catalog;

    public TtsVoiceService(TtsVoiceCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * @param language optional BCP-47 filter; blank or null returns the whole catalog
     * @return the voices {@code POST /api/campaigns} will accept
     */
    public List<TtsVoice> voices(String language) {
        return catalog.forLanguage(language);
    }
}
