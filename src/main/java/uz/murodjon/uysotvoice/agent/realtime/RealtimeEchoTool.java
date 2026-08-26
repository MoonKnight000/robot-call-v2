package uz.murodjon.uysotvoice.agent.realtime;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

import uz.murodjon.uysotvoice.agent.audio.Resampler;
import uz.murodjon.uysotvoice.agent.rtp.WavAudio;
import uz.murodjon.uysotvoice.agent.rtp.WavReader;
import uz.murodjon.uysotvoice.agent.rtp.WavRecorder;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * One turn against a realtime engine, from a WAV file, so the question stage 3 exists to
 * answer can be answered by listening: <em>does Gemini Live speak understandable
 * Uzbek?</em> No opinion settles that and no unit test reaches it — the reply has to be
 * played back.
 *
 * <p>Runs without Spring, like {@code SttComparisonTool}: a {@code main} started from the
 * IDE that feeds one recording to the engine at real time, keeps the line open with
 * silence while it answers, and writes the exchange to
 * {@code <input>-realtime-reply.wav} — a two-channel file in the same shape as a call
 * recording (caller left, engine right), so the timing of the answer is audible too, not
 * just its content.
 *
 * <p>Usage: run with the working directory at the project root and one WAV as the
 * argument. {@code --language=ru-RU} changes the conversation language (default
 * {@code uz-UZ}); {@code --prompt=...} replaces the instructions the engine runs under.
 *
 * <p>Credentials come from the same environment the app uses: {@code GEMINI_API_KEY}
 * (the key Spring AI's chat client already uses), optionally {@code GEMINI_LIVE_MODEL},
 * {@code GEMINI_LIVE_VOICE} and {@code REALTIME_GEMINI_URL}.
 *
 * <p>Pacing is deliberate: the engine does its own endpointing, and it decides the caller
 * has stopped talking by hearing silence. Audio pushed faster than real time reaches it
 * as one impossibly fast utterance, and audio that simply stops without trailing silence
 * never ends a turn at all.
 */
public final class RealtimeEchoTool {

    private static final int TELEPHONE_RATE = 8000;
    private static final int FRAME_MS = 20;
    private static final int FRAME_SAMPLES = TELEPHONE_RATE * FRAME_MS / 1000;
    /** How long the engine gets to answer once the recording has been played out. */
    private static final long REPLY_TIMEOUT_MS = 30_000;

    private RealtimeEchoTool() {
    }

    public static void main(String[] args) throws Exception {
        // Outside Spring, Logback falls back to a DEBUG root logger and the HTTP client
        // drowns the transcripts.
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);

        String language = env("STT_DEFAULT_LANGUAGE", "uz-UZ");
        String prompt = "Siz O'zbekistondagi kompaniyaning telefon operatorisiz. "
                + "Qisqa, tabiiy va muloyim javob bering. Faqat mijoz gapirgan tilda gapiring.";
        Path input = null;
        for (String arg : args) {
            if (arg.startsWith("--language=")) {
                language = arg.substring("--language=".length());
            } else if (arg.startsWith("--prompt=")) {
                prompt = arg.substring("--prompt=".length());
            } else {
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
                        15)));
        provider.init();

        WavAudio audio = WavReader.read(input);
        Path output = Path.of(input.toString().replaceFirst("\\.wav$", "") + "-realtime-reply.wav");
        System.out.printf("Feeding %s (%d Hz, %.1fs) to %s in %s%n",
                input.getFileName(), audio.sampleRate(),
                audio.samples().length / (double) audio.sampleRate(), provider.name(), language);

        try (WavRecorder recorder = new WavRecorder(output, TELEPHONE_RATE)) {
            EchoListener listener = new EchoListener(recorder, provider.outputSampleRate());
            RealtimeSession session = provider.startSession(
                    new RealtimeCallConfig("echo-tool", language, prompt, null, List.of()), listener);
            try {
                playOut(session, recorder, caller(audio));
                // Silence keeps the line open exactly as an RTP leg would: it is what the
                // engine's endpointer is waiting for to decide the turn is over.
                holdTheLine(session, recorder, listener.turnDone);
            } finally {
                session.close();
            }
            System.out.println();
            System.out.println("caller  : " + listener.heard);
            System.out.println("engine  : " + listener.spoken);
            System.out.printf("audio   : %d samples of reply (%.1fs at 8 kHz)%n",
                    listener.replySamples, listener.replySamples / (double) TELEPHONE_RATE);
        }
        System.out.println("wrote   : " + output);
    }

    /** The recording as 8 kHz telephone audio, resampled if it was captured at 16 kHz. */
    private static short[] caller(WavAudio audio) {
        if (audio.sampleRate() == TELEPHONE_RATE) {
            return audio.samples();
        }
        if (audio.sampleRate() == 16000) {
            return Resampler.downsample16kTo8k(audio.samples(), audio.samples().length);
        }
        throw new IllegalArgumentException("expected an 8 kHz or 16 kHz WAV, got " + audio.sampleRate() + " Hz");
    }

    /** Push the recording up the session one 20 ms frame at a time, paced at real time. */
    private static void playOut(RealtimeSession session, WavRecorder recorder, short[] pcm8k) throws Exception {
        long deadline = System.nanoTime();
        for (int offset = 0; offset < pcm8k.length; offset += FRAME_SAMPLES) {
            int len = Math.min(FRAME_SAMPLES, pcm8k.length - offset);
            short[] frame = new short[len];
            System.arraycopy(pcm8k, offset, frame, 0, len);
            session.sendAudio(Resampler.upsample8kTo16k(frame, len));
            recorder.writeCaller(frame, len);
            deadline = sleepUntil(deadline);
        }
    }

    /** Feed silence until the engine finishes its turn, or until it has had long enough. */
    private static void holdTheLine(RealtimeSession session, WavRecorder recorder, CountDownLatch turnDone)
            throws Exception {
        short[] silence = new short[FRAME_SAMPLES];
        long deadline = System.nanoTime();
        long frames = REPLY_TIMEOUT_MS / FRAME_MS;
        for (long i = 0; i < frames && turnDone.getCount() > 0; i++) {
            session.sendAudio(Resampler.upsample8kTo16k(silence, silence.length));
            recorder.writeCaller(silence, silence.length);
            deadline = sleepUntil(deadline);
        }
        // A short tail so the last of the reply is paired into the recording rather than
        // cut off with the loop.
        for (int i = 0; i < 25; i++) {
            recorder.writeCaller(silence, silence.length);
        }
        turnDone.await(1, TimeUnit.SECONDS);
    }

    /** Sleep until the next frame is due, returning the deadline after it. */
    private static long sleepUntil(long deadline) throws InterruptedException {
        long next = deadline + FRAME_MS * 1_000_000L;
        long waitNs = next - System.nanoTime();
        if (waitNs > 0) {
            TimeUnit.NANOSECONDS.sleep(waitNs);
        }
        return next;
    }

    /** Collects the engine's reply into the recording and its words onto the console. */
    private static final class EchoListener implements RealtimeListener {

        private final WavRecorder recorder;
        private final int engineRate;
        private final CountDownLatch turnDone = new CountDownLatch(1);
        private volatile String heard = "";
        private volatile String spoken = "";
        private volatile int replySamples;

        private EchoListener(WavRecorder recorder, int engineRate) {
            this.recorder = recorder;
            this.engineRate = engineRate;
        }

        @Override
        public void onBotAudio(short[] pcm) {
            short[] telephone = engineRate == 24000
                    ? Resampler.downsample24kTo8k(pcm, pcm.length)
                    : Resampler.downsample16kTo8k(pcm, pcm.length);
            replySamples += telephone.length;
            recorder.writeBot(telephone, telephone.length);
        }

        @Override
        public void onInputTranscript(String text, boolean isFinal) {
            if (isFinal) {
                heard = text;
            }
        }

        @Override
        public void onOutputTranscript(String text) {
            spoken = text;
        }

        @Override
        public void onInterrupted() {
            System.out.println("[interrupted]");
        }

        @Override
        public void onTurnComplete() {
            turnDone.countDown();
        }

        @Override
        public void onToolCall(String callId, String name, String argumentsJson) {
            System.out.printf("[tool] %s(%s)%n", name, argumentsJson);
        }

        @Override
        public void onClosed(Throwable cause) {
            if (cause != null) {
                System.out.println("[closed] " + cause.getMessage());
            }
            turnDone.countDown();
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
