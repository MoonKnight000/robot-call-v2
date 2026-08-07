package uz.murodjon.uysotvoice.voice.dto;

/**
 * Everything that shapes how one utterance is spoken, resolved for a single call (§11
 * settings, {@code agent.tts.TtsRouter}) — every field {@code null} means "no override,
 * use the provider's own configured default."
 *
 * <p>{@code provider}/{@code speed}/{@code pitch} come from the company's {@link
 * VoiceSettings}; {@code role} comes from the chosen voice's {@code tts_voice} row and is
 * filled in by {@code TtsRouter} while routing, since only the catalog knows which role a
 * given voice actually accepts.
 */
public record EffectiveVoiceSettings(String provider, Double speed, Double pitch, String role) {

    public static final EffectiveVoiceSettings NONE = new EffectiveVoiceSettings(null, null, null, null);

    /** The same settings speaking with {@code role} — how the catalog's role is layered in. */
    public EffectiveVoiceSettings withRole(String role) {
        return new EffectiveVoiceSettings(provider, speed, pitch, role);
    }
}
