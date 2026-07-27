package uz.murodjon.uysotvoice.agent.tts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The voices a campaign may be created with, from {@code voice-agent.tts.catalog}
 * (PROJECT.md §2.5). Lookup is by the id stored on the campaign row.
 *
 * <p>Config order is preserved so the campaign UI lists voices in the order an
 * operator was meant to see them, and entries missing an id/provider/name are dropped
 * at startup: an unusable option in the picker turns into a call spoken by the wrong
 * voice, which is only noticed by the person on the other end.
 */
@Component
public class TtsVoiceCatalog {

    private static final Logger log = LoggerFactory.getLogger(TtsVoiceCatalog.class);

    private final Map<String, TtsProperties.Voice> byId = new LinkedHashMap<>();

    public TtsVoiceCatalog(TtsProperties props) {
        List<TtsProperties.Voice> configured = props.catalog();
        if (configured != null) {
            for (TtsProperties.Voice voice : configured) {
                if (voice == null || isBlank(voice.id()) || isBlank(voice.provider()) || isBlank(voice.name())) {
                    log.warn("Ignoring incomplete TTS catalog entry {} (id, provider and name are required)", voice);
                    continue;
                }
                byId.put(key(voice.id()), voice);
            }
        }
        log.info("TTS voice catalog: {}", byId.isEmpty() ? "empty (campaigns use the configured routing)" : byId.keySet());
    }

    /** Every selectable voice, in configuration order. */
    public List<TtsProperties.Voice> all() {
        return List.copyOf(byId.values());
    }

    /** The voice with this id, or {@code null} for a blank or unknown id. */
    public TtsProperties.Voice find(String id) {
        return isBlank(id) ? null : byId.get(key(id));
    }

    /** Ids accepted by the campaign API — what an invalid choice is reported against. */
    public List<String> ids() {
        return byId.values().stream().map(TtsProperties.Voice::id).toList();
    }

    private static String key(String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
