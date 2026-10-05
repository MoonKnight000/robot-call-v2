package uz.murodjon.robotcallv2.voice.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * The daily pull of voices from each TTS provider's own list API ({@code voice-agent.voice-sync.*}).
 *
 * @param enabled        master switch; off leaves the catalog to the seed alone
 * @param languages      BCP-47 languages a synced voice is filed under (uz-UZ, ru-RU, ...)
 * @param intervalMs     pause between two syncs (default one day)
 * @param initialDelayMs pause after startup before the first sync
 */
@ConfigurationProperties(prefix = "voice-agent.voice-sync")
public record TtsVoiceSyncProperties(
        boolean enabled,
        List<String> languages,
        long intervalMs,
        long initialDelayMs
) {
    public TtsVoiceSyncProperties {
        if (languages == null) {
            languages = List.of();
        }
    }
}
