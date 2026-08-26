package uz.murodjon.uysotvoice.engine.dto;

import java.util.List;

/**
 * {@code GET /api/settings/engine/options} (§11) — which engines this build can
 * actually be set to, so the settings screen offers a closed list instead of a free-text
 * field the backend then rejects.
 *
 * <p>Read from the providers registered in the running app, not from a constant: a
 * provider that was not compiled in, or whose credentials are missing, must not be
 * offered.
 *
 * @param stt      selectable STT provider ids for {@code CASCADE}
 * @param tts      selectable TTS provider ids for {@code CASCADE}
 * @param realtime selectable engine ids for {@code REALTIME}; empty until a realtime
 *                 engine is wired, and an empty list is what tells the frontend to leave
 *                 the mode switch off
 */
public record EngineOptions(
        List<String> stt,
        List<String> tts,
        List<String> realtime
) {
}
