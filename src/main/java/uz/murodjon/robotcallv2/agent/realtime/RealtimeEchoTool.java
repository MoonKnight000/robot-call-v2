package uz.murodjon.robotcallv2.agent.realtime;

import uz.murodjon.robotcallv2.agent.rtp.WavAudio;
import uz.murodjon.robotcallv2.agent.rtp.WavHeader;
import uz.murodjon.robotcallv2.agent.rtp.WavReader;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Feeds a WAV file into Gemini Live and records its spoken reply to a new WAV file.
 *
 * <p>Offline counterpart to {@code CallComparisonTool}: tests the realtime path directly
 * from audio, without Asterisk or a SIP phone. Use it to check whether a voice prompt or
 * system instruction change actually sounds natural before testing on live calls.
 *
 * <p>Usage:
 * <pre>
 *   # Minimal (reads GEMINI_API_KEY from environment, uses default test prompt):
 *   java -cp ... uz.murodjon.robotcallv2.agent.realtime.RealtimeEchoTool input.wav
 *
 *   # Custom prompt + language:
 *   java -cp ... uz.murodjon.robotcallv2.agent.realtime.RealtimeEchoTool input.wav \
 *       --language=uz-UZ \
 *       --prompt="Qarzdorlikni eslatuvchi bank xodimi kabi javob bering."
 * </pre>
 *
 * <p>Writes {@code <input>-realtime-reply.wav} alongside the input file.
 */
public final class RealtimeEchoTool {

    private static final int TELEPHONE_RATE = 8000;
    private static final int PCM_FRAME_SAMPLES = 160; // 20ms @ 8kHz

    private RealtimeEchoTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: RealtimeEchoTool <file.wav> [--language=uz-UZ] [--prompt=...]");
            return;
        }

        Path input = null;
        String language = "uz-UZ";
        String prompt = "Siz bank xodimisiz. Mijoz bilan xushmuomala gaplashing va qisqa javob bering.";

        for (String arg : args) {
            if (arg.startsWith("--language=")) {
                language = arg.substring("--language=".length());
            } else if (arg.startsWith("--prompt=")) {
                prompt = arg.substring("--prompt=".length());
            } else if (!arg.startsWith("--")) {
                input = Path.of(arg);
            }
        }
        if (input == null) {
            System.err.println("usage: RealtimeEchoTool <file.wav> [--language=uz-UZ] [--prompt=...]");
            return;
        }

        String apiKey = env("GEMINI_API_KEY", "");
        if (apiKey.isBlank()) {
            System.err.println("GEMINI_API_KEY is not set — the same key the Spring AI chat client uses.");
            return;
        }
        GeminiLiveProvider provider = new GeminiLiveProvider(new RealtimeProperties(true, "gemini-live", true,
                new GeminiLiveProperties(apiKey,
                        env("REALTIME_GEMINI_URL",
                                "wss://generativelanguage.googleapis.com/ws/"
                                        + "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"),
                        env("GEMINI_LIVE_MODEL", "gemini-3.1-flash-live-preview"),
                        env("GEMINI_LIVE_VOICE", "Aoede"),
                        15),
                null, null, null));
        provider.init();

        WavAudio audio = WavReader.read(input);
        Path output = Path.of(input.toString().replaceFirst("\\.wav$", "") + "-realtime-reply.wav");
        System.out.printf("Feeding %s (%d Hz, %.1fs) to %s in %s%n",
                input.getFileName(), audio.sampleRate(),
                audio.samples().length / (double) audio.sampleRate(), provider.name(), language);

        EchoListener listener = new EchoListener(provider.outputSampleRate());
        RealtimeCallConfig config = new RealtimeCallConfig(
                "echo-" + System.currentTimeMillis(),
                language,
                prompt,
                null,
                List.of()
        );
        RealtimeSession session = provider.startSession(config, listener);

        // Give the session 500ms to settle its setup handshake before feeding audio.
        Thread.sleep(500);

        // Feed the input file in real-time 20ms chunks.
        short[] samples = audio.samples();
        for (int offset = 0; offset < samples.length; offset += PCM_FRAME_SAMPLES) {
            int count = Math.min(PCM_FRAME_SAMPLES, samples.length - offset);
            short[] frame = new short[PCM_FRAME_SAMPLES];
            System.arraycopy(samples, offset, frame, 0, count);
            session.sendAudio(frame);
            Thread.sleep(20);
        }
        System.out.println("Finished streaming audio; waiting for reply...");

        // Wait up to 10s for the reply to finish.
        boolean completed = listener.awaitCompletion(10, TimeUnit.SECONDS);
        session.close();

        if (!completed) {
            System.err.println("WARN: timed out waiting for complete turn — reply may be truncated");
        }

        byte[] pcmBytes = listener.getPcmData();
        if (pcmBytes.length > 0) {
            try (FileOutputStream fos = new FileOutputStream(output.toFile())) {
                fos.write(WavHeader.bytes(TELEPHONE_RATE, 1, pcmBytes.length));
                fos.write(pcmBytes);
            }
            System.out.printf("Saved reply audio to %s (%d bytes)%n", output, Files.size(output));
        } else {
            System.err.println("No reply audio received — check the GEMINI_API_KEY and model availability.");
        }
    }

    private static String env(String key, String fallback) {
        String val = System.getenv(key);
        return val != null && !val.isBlank() ? val : fallback;
    }

    private static final class EchoListener implements RealtimeListener {

        private final int outputRate;
        private final CountDownLatch done = new CountDownLatch(1);
        private final AtomicBoolean gotAudio = new AtomicBoolean(false);
        private final ByteArrayOutputStream pcmOut = new ByteArrayOutputStream();

        private EchoListener(int outputRate) {
            this.outputRate = outputRate;
        }

        @Override
        public synchronized void onBotAudio(short[] pcm24k) {
            gotAudio.set(true);
            // Downsample from the provider's output rate (e.g. 24 kHz) to telephone 8 kHz.
            if (outputRate == 24000) {
                for (int i = 0; i < pcm24k.length; i += 3) {
                    short s = pcm24k[i];
                    pcmOut.write(s & 0xFF);
                    pcmOut.write((s >> 8) & 0xFF);
                }
            } else {
                for (short s : pcm24k) {
                    pcmOut.write(s & 0xFF);
                    pcmOut.write((s >> 8) & 0xFF);
                }
            }
        }

        @Override
        public void onInputTranscript(String text, boolean isFinal) {
            System.out.printf("Input transcript (%s): %s%n", isFinal ? "final" : "interim", text);
        }

        @Override
        public void onOutputTranscript(String text) {
            System.out.printf("Bot transcript: %s%n", text);
        }

        @Override
        public void onTurnComplete() {
            System.out.println("Gemini marked turn complete.");
            done.countDown();
        }

        @Override
        public void onInterrupted() {
            System.out.println("Interruption event received.");
        }

        @Override
        public void onToolCall(String callId, String name, String argumentsJson) {
            System.out.printf("Tool call: %s(%s)%n", name, argumentsJson);
        }

        @Override
        public void onClosed(Throwable cause) {
            if (cause != null) {
                System.err.printf("Realtime closed with error: %s%n", cause.getMessage());
            }
            done.countDown();
        }

        synchronized byte[] getPcmData() {
            return pcmOut.toByteArray();
        }

        boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
            return done.await(timeout, unit);
        }
    }
}
