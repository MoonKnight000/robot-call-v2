package uz.murodjon.robotcallv2.voice.domain.entity;

/**
 * Everything that shapes how one utterance is spoken.
 *
 * @param fallbackVoice catalog id of the voice to speak with when the call's own provider fails;
 *                      null lets the router pick any other provider that speaks the language
 */
public record EffectiveVoiceSettings(String provider, Double speed, Double pitch, String role, String fallbackVoice) {

    public static final EffectiveVoiceSettings NONE = new EffectiveVoiceSettings(null, null, null, null, null);

    /**
     * The same settings spoken by {@code provider} — the agent's engine choice over the
     * company's default, applied when the call's voice does not name a provider itself.
     */
    public EffectiveVoiceSettings withProvider(String provider) {
        return new EffectiveVoiceSettings(provider, speed, pitch, role, fallbackVoice);
    }

    /** The same settings speaking with {@code role}. */
    public EffectiveVoiceSettings withRole(String role) {
        return new EffectiveVoiceSettings(provider, speed, pitch, role, fallbackVoice);
    }

    /** The same settings speaking with a modified speed. */
    public EffectiveVoiceSettings withSpeed(Double speed) {
        return new EffectiveVoiceSettings(provider, speed, pitch, role, fallbackVoice);
    }

    /** The same settings, falling back to {@code fallbackVoice} when the provider fails. */
    public EffectiveVoiceSettings withFallbackVoice(String fallbackVoice) {
        return new EffectiveVoiceSettings(provider, speed, pitch, role, fallbackVoice);
    }
}
