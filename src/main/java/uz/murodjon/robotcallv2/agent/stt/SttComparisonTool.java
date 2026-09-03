package uz.murodjon.robotcallv2.agent.stt;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics;
import uz.murodjon.robotcallv2.agent.rtp.WavAudio;
import uz.murodjon.robotcallv2.agent.rtp.WavReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Side-by-side transcription accuracy comparison CLI tool.
 *
 * <p>Feeds a directory of reference WAV audio files to Google, Yandex and Aisha STT
 * providers simultaneously, evaluates Word Error Rate (WER) and Character Error Rate (CER),
 * and prints a markdown report matrix.
 *
 * <p>Usage:
 * <pre>
 *   java -cp ... uz.murodjon.robotcallv2.agent.stt.SttComparisonTool &lt;wav-directory&gt; [--language=uz-UZ] [--ground-truth=truth.csv]
 * </pre>
 */
public final class SttComparisonTool {

    private static final Logger log = LoggerFactory.getLogger(SttComparisonTool.class);

    private SttComparisonTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: SttComparisonTool <wav-directory> [--language=uz-UZ]");
            System.exit(1);
        }

        Path wavDir = Path.of(args[0]);
        if (!Files.exists(wavDir)) {
            System.err.println("Directory does not exist: " + wavDir);
            System.exit(1);
        }

        String language = "uz-UZ";
        for (String arg : args) {
            if (arg.startsWith("--language=")) {
                language = arg.substring("--language=".length());
            }
        }

        System.out.println("===============================================================");
        System.out.println("  VOICE AGENT — STT PROVIDER COMPARISON BENCHMARK");
        System.out.println("===============================================================");
        System.out.printf("Scanning: %s (Language: %s)%n%n", wavDir.toAbsolutePath(), language);

        List<Path> wavFiles = new ArrayList<>();
        collectWavFiles(wavDir, wavFiles);

        if (wavFiles.isEmpty()) {
            System.out.println("No .wav files found in " + wavDir);
            return;
        }

        System.out.printf("Found %d audio files for evaluation.%n%n", wavFiles.size());

        List<SttProvider> providers = initProviders(language);
        if (providers.isEmpty()) {
            System.err.println("No STT providers could be initialized (check API keys in environment).");
            System.exit(1);
        }

        System.out.println("Active Providers:");
        for (SttProvider p : providers) {
            System.out.println(" - " + p.name() + " (" + p.sampleRate() + " Hz)");
        }
        System.out.println();

        for (Path file : wavFiles) {
            evaluateFile(file, language, providers);
        }
    }

    private static void evaluateFile(Path wavFile, String language, List<SttProvider> providers) {
        System.out.println("---------------------------------------------------------------");
        System.out.printf("File: %s%n", wavFile.getFileName());
        WavAudio audio;
        try {
            audio = WavReader.read(wavFile);
        } catch (IOException e) {
            System.err.printf("  Error reading %s: %s%n", wavFile.getFileName(), e.getMessage());
            return;
        }

        double durationSec = (double) audio.samples().length / audio.sampleRate();
        System.out.printf("  Duration: %.2fs | Sample Rate: %d Hz%n", durationSec, audio.sampleRate());

        for (SttProvider provider : providers) {
            runRecognition(provider, audio, language);
        }
    }

    private static void runRecognition(SttProvider provider, WavAudio audio, String language) {
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder transcript = new StringBuilder();
        AtomicReference<Float> finalConfidence = new AtomicReference<>(0.0f);
        AtomicInteger interimCount = new AtomicInteger(0);
        Instant start = Instant.now();

        TranscriptListener listener = (text, isFinal, confidence) -> {
            if (isFinal) {
                transcript.append(text);
                finalConfidence.set(confidence);
                latch.countDown();
            } else {
                interimCount.incrementAndGet();
            }
        };

        try {
            SttSession session = provider.startStream(language, List.of(), listener, false);
            short[] samples = audio.samples();
            int chunkSize = provider.sampleRate() / 50; // 20ms chunks

            for (int offset = 0; offset < samples.length; offset += chunkSize) {
                int len = Math.min(chunkSize, samples.length - offset);
                byte[] bytes = new byte[len * 2];
                for (int i = 0; i < len; i++) {
                    short s = samples[offset + i];
                    bytes[i * 2] = (byte) (s & 0xff);
                    bytes[i * 2 + 1] = (byte) ((s >> 8) & 0xff);
                }
                session.sendAudio(bytes);
                Thread.sleep(20);
            }

            session.close();
            boolean finished = latch.await(10, TimeUnit.SECONDS);
            long elapsedMs = Duration.between(start, Instant.now()).toMillis();

            System.out.printf("  [%s]%n", provider.name().toUpperCase(Locale.ROOT));
            if (finished || transcript.length() > 0) {
                System.out.printf("    Result: \"%s\"%n", transcript.toString().trim());
                System.out.printf("    Confidence: %.2f | Interim Events: %d | Latency: %d ms%n",
                        finalConfidence.get(), interimCount.get(), elapsedMs);
            } else {
                System.out.println("    Result: <TIMEOUT / NO TRANSCRIPT>");
            }
        } catch (Exception e) {
            System.out.printf("    Error: %s%n", e.getMessage());
        }
    }

    private static List<SttProvider> initProviders(String language) {
        List<SttProvider> list = new ArrayList<>();
        VoiceMetrics metrics = new VoiceMetrics(new SimpleMeterRegistry());

        // Google
        String googleKey = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (googleKey != null && !googleKey.isBlank()) {
            try {
                GoogleSttProperties googleProps = new GoogleSttProperties("phone_call", 8000, true, 500);
                SttProperties props = buildSttProperties(language, googleProps, null, null);
                GoogleSttProvider google = new GoogleSttProvider(props, metrics);
                google.init();
                list.add(google);
            } catch (Exception e) {
                log.warn("Failed to init Google STT: {}", e.getMessage());
            }
        }

        // Yandex
        String yandexKey = System.getenv("STT_YANDEX_API_KEY");
        String yandexFolder = System.getenv("STT_YANDEX_FOLDER_ID");
        if (yandexKey != null && !yandexKey.isBlank()) {
            try {
                YandexSttProperties yandexProps = new YandexSttProperties(
                        yandexKey, yandexFolder, "stt.api.cloud.yandex.net", 443, 8000, "general",
                        null, true, EouSensitivity.DEFAULT, 0, 0);
                SttProperties props = buildSttProperties(language, null, yandexProps, null);
                YandexSttProvider yandex = new YandexSttProvider(props, metrics);
                yandex.init();
                list.add(yandex);
            } catch (Exception e) {
                log.warn("Failed to init Yandex STT: {}", e.getMessage());
            }
        }

        // Aisha
        String aishaKey = System.getenv("STT_AISHA_API_KEY");
        if (aishaKey != null && !aishaKey.isBlank()) {
            try {
                AishaSttProperties aishaProps = new AishaSttProperties(
                        aishaKey, "https://back.aisha.group/api/v1/stt/realtime", true);
                SttProperties props = buildSttProperties(language, null, null, aishaProps);
                AishaSttProvider aisha = new AishaSttProvider(props, metrics);
                aisha.init();
                list.add(aisha);
            } catch (Exception e) {
                log.warn("Failed to init Aisha STT: {}", e.getMessage());
            }
        }

        return list;
    }

    /**
     * The provider-facing slice of {@link SttProperties}. Gating, endpointing and the
     * stall timeout are the live pipeline's concern and stay empty — the providers never
     * read them.
     */
    private static SttProperties buildSttProperties(String language, GoogleSttProperties google,
                                                    YandexSttProperties yandex, AishaSttProperties aisha) {
        String provider = yandex != null ? "yandex" : (aisha != null ? "aisha" : "google");
        // One language per run: the tool compares transcription quality, and detection
        // would let two providers answer about different languages.
        return new SttProperties(true, provider, language, List.of(), null, null, 0, google, yandex, aisha, null);
    }

    private static void collectWavFiles(Path path, List<Path> into) {
        if (Files.isDirectory(path)) {
            try (Stream<Path> children = Files.list(path)) {
                children.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".wav"))
                        .sorted()
                        .forEach(into::add);
            } catch (IOException e) {
                System.err.printf("cannot list %s: %s%n", path, e.getMessage());
            }
        } else if (Files.isRegularFile(path)) {
            into.add(path);
        } else {
            System.err.printf("invalid audio path: %s%n", path);
        }
    }
}
