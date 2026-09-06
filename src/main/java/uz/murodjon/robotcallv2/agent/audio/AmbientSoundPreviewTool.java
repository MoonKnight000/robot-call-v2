package uz.murodjon.robotcallv2.agent.audio;

import uz.murodjon.robotcallv2.agent.rtp.WavAudio;
import uz.murodjon.robotcallv2.agent.rtp.WavHeader;
import uz.murodjon.robotcallv2.agent.rtp.WavReader;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AmbientSound;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI tool for hearing each {@link AmbientSound} without placing a call.
 *
 * <p>Whether a background bed sounds like a room is not a thing a test can assert, and
 * dialing out to find out costs a real call and a real person. This writes the beds to
 * WAV instead — on their own, and, given a speech recording, mixed underneath it at the
 * exact level {@code RtpEndpoint} would use, which is the part actually worth judging.
 *
 * <p>{@code AmbientSoundPreviewTool <out-dir> [speech.wav]}
 */
public class AmbientSoundPreviewTool {

    private static final int SAMPLE_RATE = 8000;
    private static final int FRAME_SAMPLES = 160; // 20 ms, as the pacer sends
    /** One full loop, so a preview is enough to hear whether the bed repeats. */
    private static final int PREVIEW_SECONDS = 60;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: AmbientSoundPreviewTool <out-dir> [speech.wav]");
            System.exit(1);
        }
        Path outDir = Path.of(args[0]);
        Files.createDirectories(outDir);

        short[] speech = null;
        if (args.length > 1) {
            WavAudio audio = WavReader.read(Path.of(args[1]));
            if (audio.sampleRate() != SAMPLE_RATE) {
                System.err.printf("speech.wav is %d Hz — this tool expects %d Hz telephone audio%n",
                        audio.sampleRate(), SAMPLE_RATE);
                System.exit(1);
            }
            speech = audio.samples();
            System.out.printf("Mixing under %s (%.1fs), RMS %.0f%n",
                    args[1], speech.length / (double) SAMPLE_RATE, rms(speech));
        }

        for (AmbientSound sound : AmbientSound.values()) {
            if (sound == AmbientSound.OFF) {
                continue;
            }
            write(outDir.resolve(sound.name().toLowerCase() + ".wav"),
                    render(new short[PREVIEW_SECONDS * SAMPLE_RATE], sound));
            if (speech != null) {
                write(outDir.resolve(sound.name().toLowerCase() + "-under-speech.wav"),
                        render(speech.clone(), sound));
            }
        }
        System.out.println("Wrote previews to " + outDir.toAbsolutePath());
    }

    /** Runs {@code source} through the mixer frame by frame, exactly as the pacer does. */
    private static short[] render(short[] source, AmbientSound sound) {
        short[] output = new short[source.length];
        for (int offset = 0; offset < source.length; offset += FRAME_SAMPLES) {
            int length = Math.min(FRAME_SAMPLES, source.length - offset);
            short[] frame = new short[length];
            System.arraycopy(source, offset, frame, 0, length);
            short[] mixed = AmbientSoundGenerator.mix(frame, sound, offset);
            System.arraycopy(mixed, 0, output, offset, length);
        }
        System.out.printf("%-13s RMS %.0f%n", sound, rms(output));
        return output;
    }

    private static void write(Path file, short[] samples) throws Exception {
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write(WavHeader.bytes(SAMPLE_RATE, 1, samples.length * 2));
            byte[] pcm = new byte[samples.length * 2];
            for (int i = 0; i < samples.length; i++) {
                pcm[i * 2] = (byte) (samples[i] & 0xFF);
                pcm[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
            }
            out.write(pcm);
        }
    }

    private static double rms(short[] samples) {
        double sumSquares = 0;
        for (short sample : samples) {
            sumSquares += (double) sample * sample;
        }
        return Math.sqrt(sumSquares / samples.length);
    }
}
