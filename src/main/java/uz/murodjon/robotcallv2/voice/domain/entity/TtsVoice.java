package uz.murodjon.robotcallv2.voice.domain.entity;

/**
 * A row of tts_voice (PROJECT.md §2.5) — one selectable voice.
 *
 * @param id       stable id stored on the campaign (e.g. nigora)
 * @param provider provider that owns the voice (yandex/google/aisha)
 * @param language BCP-47 language the voice speaks
 * @param name     provider-side voice name sent with the synthesis request
 * @param label    human-readable name shown in the campaign UI
 * @param role     provider-side speaking role
 */
public record TtsVoice(
        String id,
        String provider,
        String language,
        String name,
        String label,
        String role
) {
}
