package uz.murodjon.uysotvoice.voice.dto;

/**
 * Everything that shapes how one utterance is spoken, resolved for a single call (§11
 * settings, {@code agent.tts.TtsRouter}) — every field {@code null} means "no override,
 * use the provider's own configured default."
 *
 * <p>The three fields come from three different places, and all three only meet here,
 * on the way to {@code TtsRouter}: {@code provider} from the company's {@code
 * engine_config}, {@code speed}/{@code pitch} from its {@link VoiceSettings}, and
 * {@code role} from the chosen voice's {@code tts_voice} row — filled in by {@code
 * TtsRouter} while routing, since only the catalog knows which role a given voice
 * actually accepts.
 */
public record EffectiveVoiceSettings(String provider, Double speed, Double pitch, String role) {

    public static final EffectiveVoiceSettings NONE = new EffectiveVoiceSettings(null, null, null, null);

    /** The same settings speaking with {@code role} — how the catalog's role is layered in. */
    public EffectiveVoiceSettings withRole(String role) {
        return new EffectiveVoiceSettings(provider, speed, pitch, role);
    }

    /** The same settings speaking with a modified speed (e.g. for emotion-adaptive pacing). */
    public EffectiveVoiceSettings withSpeed(Double speed) {
        return new EffectiveVoiceSettings(provider, speed, pitch, role);
    }
}
