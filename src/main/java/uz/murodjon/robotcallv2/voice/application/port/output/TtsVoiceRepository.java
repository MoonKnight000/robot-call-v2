package uz.murodjon.robotcallv2.voice.application.port.output;

import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;

import java.time.Instant;
import java.util.List;

public interface TtsVoiceRepository {

    /** Active voices only — what a picker may offer. */
    List<TtsVoice> findAll();

    /** Active voices only, narrowed to the language's prefix; blank means every language. */
    List<TtsVoice> findByLanguage(String language);

    /** Any voice by id, switched off or not — an agent saved with one still speaks with it. */
    TtsVoice find(String id);

    /** As {@link #find}, but null for a voice that is switched off. */
    TtsVoice findActiveById(String id);

    List<String> findIds();

    /**
     * Files {@code voices} as {@code provider}'s synced voices: each is inserted or refreshed
     * and switched on, and every earlier synced voice of that provider missing from the list
     * is switched off. Seeded voices (never synced) are left alone.
     */
    void replaceSyncedVoices(String provider, List<TtsVoice> voices, Instant syncedAt);
}
