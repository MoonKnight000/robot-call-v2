package uz.murodjon.robotcallv2.voice.domain.entity;

import uz.murodjon.robotcallv2.voice.domain.enums.VoiceAge;
import uz.murodjon.robotcallv2.voice.domain.enums.VoiceGender;

/**
 * A row of tts_voice (PROJECT.md §2.5) — one selectable voice.
 *
 * @param id        stable id stored on the campaign (e.g. nigora)
 * @param provider  provider that owns the voice (yandex/google/aisha)
 * @param language  BCP-47 language the voice speaks
 * @param name      provider-side voice name sent with the synthesis request
 * @param label     human-readable name shown in the campaign UI
 * @param role      provider-side speaking role
 * @param gender    how the voice sounds, for the picker's filter; null when nobody described it
 * @param age       how old the voice sounds; null when nobody described it (most catalog voices)
 * @param companyId the company a cloned voice belongs to — only it sees and speaks the voice;
 *                  null for a shared catalog voice
 */
public record TtsVoice(
        String id,
        String provider,
        String language,
        String name,
        String label,
        String role,
        VoiceGender gender,
        VoiceAge age,
        Long companyId
) {

    /** Whether {@code companyId} may pick this voice: a shared one, or the company's own clone. */
    public boolean isVisibleTo(long companyId) {
        return this.companyId == null || this.companyId == companyId;
    }
}
