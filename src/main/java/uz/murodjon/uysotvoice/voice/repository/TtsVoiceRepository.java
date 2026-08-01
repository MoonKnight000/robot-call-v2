package uz.murodjon.uysotvoice.voice.repository;

import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.voice.dto.TtsVoiceRow;
import uz.murodjon.uysotvoice.voice.entity.TtsVoice;

import java.util.List;
import java.util.Locale;

/** DAO for {@code tts_voice} (PROJECT.md §2.5, §6). */
@Repository
public class TtsVoiceRepository {

    private final TtsVoiceJpaRepository jpa;

    public TtsVoiceRepository(TtsVoiceJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** Every selectable voice, in id order. */
    public List<TtsVoiceRow> all() {
        return jpa.findAllByOrderByIdAsc().stream().map(TtsVoiceRepository::toRow).toList();
    }

    /**
     * Voices that can speak {@code language} (matched by BCP-47 primary subtag), or every
     * voice when {@code language} is blank — so a form that already knows the campaign
     * language only offers voices that can speak it.
     */
    public List<TtsVoiceRow> forLanguage(String language) {
        if (isBlank(language)) {
            return all();
        }
        String wanted = languagePrefix(language);
        return jpa.findAllByOrderByIdAsc().stream()
                .filter(v -> v.getLanguage() != null && languagePrefix(v.getLanguage()).equals(wanted))
                .map(TtsVoiceRepository::toRow)
                .toList();
    }

    /** The voice with this id, or {@code null} for a blank or unknown id. */
    public TtsVoiceRow find(String id) {
        if (isBlank(id)) {
            return null;
        }
        return jpa.findById(id.trim().toLowerCase(Locale.ROOT)).map(TtsVoiceRepository::toRow).orElse(null);
    }

    /** Ids accepted by the campaign API — what an invalid choice is reported against. */
    public List<String> ids() {
        return jpa.findAllByOrderByIdAsc().stream().map(TtsVoice::getId).toList();
    }

    private static String languagePrefix(String language) {
        int dash = language.indexOf('-');
        return (dash > 0 ? language.substring(0, dash) : language).toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static TtsVoiceRow toRow(TtsVoice e) {
        return new TtsVoiceRow(e.getId(), e.getProvider(), e.getLanguage(), e.getName(), e.getLabel());
    }
}
