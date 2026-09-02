package uz.murodjon.robotcallv2.agent.rtp;

/**
 * Decoded PCM audio read from a RIFF/WAVE container.
 *
 * @param sampleRate samples per second declared in the fmt chunk
 * @param samples    16-bit PCM samples (mono)
 */
public record WavAudio(int sampleRate, short[] samples) {
}
