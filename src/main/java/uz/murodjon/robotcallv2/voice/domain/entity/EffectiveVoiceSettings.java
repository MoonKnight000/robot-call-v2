package uz.murodjon.robotcallv2.voice.domain.entity;

/**
 * Everything that shapes how one utterance is spoken.
 */
public record EffectiveVoiceSettings(String provider, Double speed, Double pitch, String role) {

    public static final EffectiveVoiceSettings NONE = new EffectiveVoiceSettings(null, null, null, null);

    /** The same settings speaking with {@code role}. */
    public EffectiveVoiceSettings withRole(String role) {
        return new EffectiveVoiceSettings(provider, speed, pitch, role);
    }

    /** The same settings speaking with a modified speed. */
    public EffectiveVoiceSettings withSpeed(Double speed) {
        return new EffectiveVoiceSettings(provider, speed, pitch, role);
    }
}
