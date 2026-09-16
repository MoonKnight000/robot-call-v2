package uz.murodjon.robotcallv2.agent.rtp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The recording is evidence in a dispute (§11.3), and the only way to tell it is wrong
 * is normally to listen to it. What is worth pinning down without ears: both directions
 * are in the file, they are on the channels they claim to be on, and the bot's last
 * words are not lost just because the caller's audio stopped first.
 */
class WavRecorderTest {

    private static final int RATE = 8000;

    private static short[] shorts(int... values) {
        short[] out = new short[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (short) values[i];
        }
        return out;
    }

    /** The recorded samples, interleaved left, right, left, right… as WAV stores them. */
    private static short[] samplesOf(Path file) throws IOException {
        byte[] raw = Files.readAllBytes(file);
        short[] out = new short[(raw.length - WavHeader.SIZE) / 2];
        for (int i = 0; i < out.length; i++) {
            int at = WavHeader.SIZE + i * 2;
            out[i] = (short) ((raw[at] & 0xFF) | (raw[at + 1] << 8));
        }
        return out;
    }

    private static int intAt(byte[] raw, int offset) {
        return (raw[offset] & 0xFF) | ((raw[offset + 1] & 0xFF) << 8)
                | ((raw[offset + 2] & 0xFF) << 16) | ((raw[offset + 3] & 0xFF) << 24);
    }

    private static int shortAt(byte[] raw, int offset) {
        return (raw[offset] & 0xFF) | ((raw[offset + 1] & 0xFF) << 8);
    }

    @Test
    void spatialStereoCrossFeedsChannels(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("call.wav");
        try (WavRecorder recorder = new WavRecorder(file, RATE, RecordingMode.SPATIAL_STEREO)) {
            recorder.writeBot(shorts(100), 1);
            recorder.writeCaller(shorts(1000), 1);
        }

        short[] samples = samplesOf(file);
        assertThat(samples).containsExactly(shorts(1035, 450));
    }

    @Test
    void declaresItselfStereo(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("call.wav");
        try (WavRecorder recorder = new WavRecorder(file, RATE)) {
            recorder.writeCaller(shorts(1, 2), 2);
        }

        byte[] raw = Files.readAllBytes(file);
        assertThat(shortAt(raw, 22)).isEqualTo(2);            // channels
        assertThat(intAt(raw, 24)).isEqualTo(RATE);           // sample rate
        assertThat(intAt(raw, 28)).isEqualTo(RATE * 4);       // byte rate: 2 channels, 16-bit
        assertThat(shortAt(raw, 32)).isEqualTo(4);            // block align
        assertThat(intAt(raw, 40)).isEqualTo(raw.length - WavHeader.SIZE); // data size
    }

    @Test
    void putsTheCallerLeftAndTheBotRight(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("call.wav");
        try (WavRecorder recorder = new WavRecorder(file, RATE)) {
            recorder.writeBot(shorts(5, 6), 2);
            recorder.writeCaller(shorts(1, 2, 3), 3);
        }

        // Third caller sample has no bot audio left to pair with — silence, not a stall.
        assertThat(samplesOf(file)).containsExactly(shorts(1, 5, 2, 6, 3, 0));
    }

    @Test
    void leavesTheBotChannelSilentWhileItIsNotSpeaking(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("call.wav");
        try (WavRecorder recorder = new WavRecorder(file, RATE)) {
            recorder.writeCaller(shorts(1, 2), 2);
        }

        assertThat(samplesOf(file)).containsExactly(shorts(1, 0, 2, 0));
    }

    @Test
    void pairsBotAudioAcrossFrameBoundaries(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("call.wav");
        try (WavRecorder recorder = new WavRecorder(file, RATE)) {
            recorder.writeBot(shorts(7), 1);
            recorder.writeBot(shorts(8, 9), 2);
            recorder.writeCaller(shorts(1, 2), 2);
            recorder.writeCaller(shorts(3), 1);
        }

        assertThat(samplesOf(file)).containsExactly(shorts(1, 7, 2, 8, 3, 9));
    }

    @Test
    void keepsTheBotsLastWordsAfterTheCallerAudioStops(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("call.wav");
        try (WavRecorder recorder = new WavRecorder(file, RATE)) {
            recorder.writeCaller(shorts(1), 1);
            // The goodbye line plays out after the caller's last inbound packet.
            recorder.writeBot(shorts(7, 8), 2);
        }

        assertThat(samplesOf(file)).containsExactly(shorts(1, 0, 0, 7, 0, 8));
    }

    /** Caller audio in 20 ms frames whose samples count up, so order and gaps are checkable. */
    private static void writeCountingCaller(WavRecorder recorder, int samples) throws IOException {
        short[] frame = new short[160];
        for (int written = 0; written < samples; written += frame.length) {
            int len = Math.min(frame.length, samples - written);
            for (int i = 0; i < len; i++) {
                frame[i] = (short) ((written + i) % Short.MAX_VALUE);
            }
            recorder.writeCaller(frame, len);
        }
    }

    @Test
    void writesEachSecondToDiskBeforeTheCallEnds(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("call.wav");
        try (WavRecorder recorder = new WavRecorder(file, RATE)) {
            writeCountingCaller(recorder, RATE * 12 / 10); // 1.2 s

            // The first full second is on disk already — a crash now loses the last 0.2 s only.
            assertThat(Files.size(file)).isEqualTo(WavHeader.SIZE + RATE * 4L);
        }
        assertThat(Files.size(file)).isEqualTo(WavHeader.SIZE + RATE * 12 / 10 * 4L);
    }

    @Test
    void headerCountsEveryBatchedByteAndKeepsTheOrder(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("call.wav");
        int samples = RATE * 5 / 2 + 7; // several full buffers plus a partial one
        try (WavRecorder recorder = new WavRecorder(file, RATE)) {
            writeCountingCaller(recorder, samples);
            recorder.writeBot(shorts(9, 9), 2); // a goodbye tail after the caller's last frame
        }

        byte[] raw = Files.readAllBytes(file);
        assertThat(intAt(raw, 40)).isEqualTo(raw.length - WavHeader.SIZE);
        assertThat(raw.length - WavHeader.SIZE).isEqualTo((samples + 2) * 4);
        short[] interleaved = samplesOf(file);
        for (int i = 0; i < samples; i++) {
            assertThat(interleaved[i * 2]).as("caller sample %d", i).isEqualTo((short) (i % Short.MAX_VALUE));
        }
        assertThat(interleaved[samples * 2 + 1]).isEqualTo((short) 9);
        assertThat(interleaved[samples * 2 + 3]).isEqualTo((short) 9);
    }

    @Test
    void writesOnlyAHeaderForACallWithNoAudioAtAll(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("call.wav");
        new WavRecorder(file, RATE).close();

        assertThat(Files.size(file)).isEqualTo(WavHeader.SIZE);
    }
}
