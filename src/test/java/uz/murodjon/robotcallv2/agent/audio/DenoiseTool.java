package uz.murodjon.robotcallv2.agent.audio;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import uz.murodjon.robotcallv2.agent.rtp.WavAudio;
import uz.murodjon.robotcallv2.agent.rtp.WavHeader;
import uz.murodjon.robotcallv2.agent.rtp.WavReader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Runs a recording through the neural denoiser exactly as a call's inbound leg would —
 * 20 ms frames, {@link NeuralDenoiseStream} — and writes the result next to it, so the
 * effect can be listened to and fed to {@code SttComparisonTool} before and after
 * (VOICE-PIPELINE-PLAN Bosqich 6). Also prints the real-time factor: the CPU share of one
 * core a single call costs.
 *
 * <p>Usage:
 * <pre>
 *   java -cp ... uz.murodjon.robotcallv2.agent.audio.DenoiseTool &lt;in.wav&gt; &lt;out.wav&gt; \
 *        --model=models/gtcrn_simple.onnx
 * </pre>
 * The input must be 8 kHz mono — what the RTP leg carries.
 */
public final class DenoiseTool {

    private static final int FRAME = 160;

    private DenoiseTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || !args[2].startsWith("--model=")) {
            System.err.println("Usage: DenoiseTool <in.wav> <out.wav> --model=<gtcrn onnx>");
            System.exit(1);
        }
        WavAudio audio = WavReader.read(Path.of(args[0]));
        if (audio.sampleRate() != 8000) {
            System.err.println("Expected 8 kHz mono, got " + audio.sampleRate() + " Hz");
            System.exit(1);
        }
        NeuralDenoiser denoiser = new NeuralDenoiser(
                new DenoiserProperties(true, args[2].substring("--model=".length())), new SimpleMeterRegistry());
        denoiser.init();
        NeuralDenoiseStream stream = denoiser.newStream();
        if (stream == null) {
            System.err.println("Model did not load — see the log above");
            System.exit(1);
        }

        short[] in = audio.samples();
        short[] out = new short[in.length];
        long started = System.nanoTime();
        for (int offset = 0; offset + FRAME <= in.length; offset += FRAME) {
            short[] frame = new short[FRAME];
            System.arraycopy(in, offset, frame, 0, FRAME);
            short[] cleaned = stream.process(frame, FRAME);
            if (cleaned == null) {
                System.err.println("Inference failed at " + offset * 1000 / 8000 + " ms");
                System.exit(1);
            }
            System.arraycopy(cleaned, 0, out, offset, FRAME);
        }
        double seconds = (System.nanoTime() - started) / 1e9;
        denoiser.shutdown();

        ByteBuffer data = ByteBuffer.allocate(out.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short s : out) {
            data.putShort(s);
        }
        Path target = Path.of(args[1]);
        Files.write(target, WavHeader.bytes(8000, 1, out.length * 2));
        Files.write(target, data.array(), java.nio.file.StandardOpenOption.APPEND);

        double audioSeconds = in.length / 8000.0;
        System.out.printf(Locale.ROOT, "%.1f s of audio in %.2f s — real-time factor %.3f (%.1f%% of one core per call)%n",
                audioSeconds, seconds, seconds / audioSeconds, 100 * seconds / audioSeconds);
        System.out.println("Wrote " + target);
    }
}
