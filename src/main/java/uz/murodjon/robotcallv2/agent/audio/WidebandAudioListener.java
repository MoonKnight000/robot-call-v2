package uz.murodjon.robotcallv2.agent.audio;

/**
 * An {@link AudioListener} that can take the caller's audio at 16 kHz when the leg carries
 * it (a browser call on a wideband leg, {@code RtpEndpoint} in wideband mode). On such a leg
 * it is given {@link #onWidebandAudio} <em>instead of</em> {@link #onAudio} — never both for
 * the same frame — and every other listener keeps getting 8 kHz.
 */
public interface WidebandAudioListener extends AudioListener {

    /**
     * @param pcm    16 kHz mono 16-bit PCM (may be larger than {@code length})
     * @param length number of valid samples at the start of {@code pcm}
     */
    void onWidebandAudio(short[] pcm, int length);
}
