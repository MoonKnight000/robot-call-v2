package uz.murodjon.uysotvoice.agent.tts;

/**
 * One selectable voice (PROJECT.md §2.5). A campaign stores {@link #id()}; the
 * catalog is what turns it back into a provider plus a provider-side voice name,
 * so the choice pins both — a voice is only ever spoken by the engine that owns it.
 *
 * @param id       stable id stored on the campaign (e.g. {@code nigora})
 * @param provider provider that owns the voice ({@code yandex}/{@code google})
 * @param language BCP-47 language the voice speaks; a call in another language
 *                 ignores it and falls back to normal routing, because a Russian
 *                 voice reading Uzbek text is worse than the default voice
 * @param name     provider-side voice name sent with the synthesis request
 * @param label    human-readable name shown in the campaign UI
 */
public record TtsVoice(
        String id,
        String provider,
        String language,
        String name,
        String label
) {
}