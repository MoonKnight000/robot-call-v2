package uz.murodjon.uysotvoice.voice.dto;

import java.time.Instant;

/**
 * {@code GET/PUT /api/settings/voice} (§11) — a company's TTS tuning overrides. Every
 * field is nullable: null means "use the process default" ({@code voice-agent.tts.*}),
 * not zero/blank.
 *
 * @param provider preferred provider id ({@code google}/{@code yandex}); used first when
 *                 it supports the call's language, else the router falls back by language
 *                 support — same rule as the process-wide default it overrides
 * @param speed    speaking rate multiplier (1.0 = normal); applied to both providers
 * @param pitch    pitch shift in semitones; Google Cloud TTS only — Yandex SpeechKit v1
 *                 has no pitch parameter, so this is ignored when Yandex is selected
 */
public record VoiceSettings(
        long companyId,
        String provider,
        Double speed,
        Double pitch,
        Instant createdAt
) {
}
