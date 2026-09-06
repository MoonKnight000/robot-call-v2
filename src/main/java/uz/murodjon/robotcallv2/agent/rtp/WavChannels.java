package uz.murodjon.robotcallv2.agent.rtp;

/**
 * A decoded WAV file with its channels kept apart.
 *
 * <p>{@link WavAudio} is the usual shape — one mono track, stereo folded down to the left
 * channel — and it is what playback and replay want. A call recording is the case where
 * that fold is exactly wrong: {@link RecordingMode#SPATIAL_STEREO} writes the caller
 * and the bot to different channels on purpose, and anything measuring who spoke when
 * needs both of them.
 *
 * @param sampleRate samples per second declared in the fmt chunk
 * @param channels   one array per channel, each of 16-bit PCM samples of equal length
 */
public record WavChannels(int sampleRate, short[][] channels) {

    /** How many channels the file carried — 1 for mono, 2 for a call recording. */
    public int count() {
        return channels.length;
    }

    /** Samples per channel. */
    public int length() {
        return channels.length == 0 ? 0 : channels[0].length;
    }
}
