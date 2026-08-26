package uz.murodjon.uysotvoice.voice.dto;

import java.time.Instant;

/**
 * {@code GET/PUT /api/settings/voice} (§11) — a company's TTS tuning overrides. Every
 * field is nullable: null means "use the process default" ({@code voice-agent.tts.*}),
 * not zero/blank.
 *
 * <p>Which provider speaks is <em>not</em> here: that moved to {@code engine_config}
 * ({@code GET/PUT /api/settings/engine}), because the same choice also covers running
 * the call on a realtime engine with no separate TTS step at all. This record is only
 * about how the voice sounds.
 *
 * @param speed speaking rate multiplier (1.0 = normal); applied by every provider
 * @param pitch pitch shift in semitones; Google Cloud TTS only — Yandex SpeechKit v1
 *              has no pitch parameter, so this is ignored when Yandex speaks the call
 */
public record VoiceSettings(
        long companyId,
        Double speed,
        Double pitch,
        Instant createdAt
) {
}
