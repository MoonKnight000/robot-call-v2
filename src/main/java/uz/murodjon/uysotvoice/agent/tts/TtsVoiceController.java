package uz.murodjon.uysotvoice.agent.tts;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The voices a campaign can be created with ({@code GET /api/tts/voices}). The campaign
 * form reads it to fill its voice picker, so the options an operator sees are exactly
 * the ids {@code POST /api/campaigns} accepts.
 */
@RestController
@RequestMapping("/api/tts")
public class TtsVoiceController {

    private final TtsVoiceCatalog catalog;

    public TtsVoiceController(TtsVoiceCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * @param language optional BCP-47 filter, so a form that already knows the campaign
     *                 language only offers voices that can speak it
     */
    @GetMapping("/voices")
    public List<TtsProperties.Voice> voices(@RequestParam(required = false) String language) {
        if (language == null || language.isBlank()) {
            return catalog.all();
        }
        String wanted = prefix(language);
        return catalog.all().stream()
                .filter(v -> v.language() != null && prefix(v.language()).equals(wanted))
                .toList();
    }

    private static String prefix(String language) {
        int dash = language.indexOf('-');
        return (dash > 0 ? language.substring(0, dash) : language).toLowerCase();
    }
}
