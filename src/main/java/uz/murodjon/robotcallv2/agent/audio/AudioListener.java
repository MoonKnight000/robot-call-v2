package uz.murodjon.robotcallv2.agent.audio;

/**
 * Receives decoded 8 kHz mono 16-bit PCM as it arrives from the call. Used to
 * fan incoming audio out to multiple consumers (recording, STT, VAD, ...).
 */
@FunctionalInterface
public interface AudioListener {

    /**
     * @param pcm    buffer holding decoded samples (may be larger than {@code length})
     * @param length number of valid samples at the start of {@code pcm}
     */
    void onAudio(short[] pcm, int length);
}
