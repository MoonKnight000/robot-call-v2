package uz.murodjon.uysotvoice.voice.dto;

/**
 * A company's {@link VoiceSettings} as the TTS pipeline actually uses them at synthesis
 * time (§11 settings, {@code agent.tts.TtsRouter}) — every field {@code null} means "no
 * override for this call, use the provider's own configured default."
 */
public record EffectiveVoiceSettings(String provider, Double speed, Double pitch) {

    public static final EffectiveVoiceSettings NONE = new EffectiveVoiceSettings(null, null, null);
}
