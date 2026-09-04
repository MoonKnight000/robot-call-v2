package uz.murodjon.robotcallv2.agent.stt;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.murodjon.robotcallv2.agent.audio.Resampler;
import uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics;
import uz.murodjon.robotcallv2.agent.rtp.WavAudio;
import uz.murodjon.robotcallv2.agent.rtp.WavReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * CLI tool for comparing STT providers side-by-side on real recordings.
 */
public class SttComparisonTool {

    private static final Logger log = LoggerFactory.getLogger(SttComparisonTool.class);

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: SttComparisonTool <wav-file-or-dir> <language-code> [truth-file]");
            System.exit(1);
        }

        Path inputPath = Path.of(args[0]);
        String language = args[1];
        Path truthPath = args.length > 2 ? Path.of(args[2]) : null;

        List<Path> wavFiles = new ArrayList<>();
        collectWavFiles(inputPath, wavFiles);

        if (wavFiles.isEmpty()) {
            System.err.println("No .wav files found at " + inputPath);
            System.exit(1);
        }

        List<SttProvider> providers = initProviders(language);
        if (providers.isEmpty()) {
            System.err.println("No STT providers available. Check environment variables.");
            System.exit(1);
        }

        System.out.printf("Comparing %d providers on %d files (language: %s)%n",
                providers.size(), wavFiles.size(), language);

        for (Path wavFile : wavFiles) {
            System.out.println("\n========================================");
            System.out.println("File: " + wavFile.getFileName());

            WavAudio audio = WavReader.read(wavFile);
            short[] samples = audio.samples();
            int sampleRate = audio.sampleRate();

            for (SttProvider provider : providers) {
                runProvider(provider, language, samples, sampleRate);
            }
        }
    }

    private static void runProvider(SttProvider provider, String language,
                                    short[] samples, int sourceRate) {
        System.out.printf("--- %s ---%n", provider.name());

        StringBuilder fullTranscript = new StringBuilder();
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean errored = new AtomicBoolean(false);

        TranscriptListener listener = (text, isFinal, confidence) -> {
            if (isFinal) {
                fullTranscript.append(text).append(" ");
            }
        };

        try {
            SttSession session = provider.startStream(language, List.of(), listener, false);

            short[] targetSamples;
            if (provider.sampleRate() == 16000 && sourceRate == 8000) {
                targetSamples = Resampler.upsample8kTo16k(samples, samples.length);
            } else {
                targetSamples = samples;
            }

            int chunkSize = provider.sampleRate() / 50; // 20ms
            byte[] buffer = new byte[chunkSize * 2];

            for (int i = 0; i < targetSamples.length; i += chunkSize) {
                int len = Math.min(chunkSize, targetSamples.length - i);
                for (int j = 0; j < len; j++) {
                    short s = targetSamples[i + j];
                    buffer[j * 2] = (byte) (s & 0xFF);
                    buffer[j * 2 + 1] = (byte) ((s >> 8) & 0xFF);
                }
                session.sendAudio(buffer);
                Thread.sleep(20);
            }

            session.endUtterance();
            done.await(5, TimeUnit.SECONDS);
            session.close();

            System.out.printf("    Result: %s%n", fullTranscript.toString().trim());
        } catch (Exception e) {
            System.out.printf("    Error: %s%n", e.getMessage());
        }
    }

    private static List<SttProvider> initProviders(String language) {
        List<SttProvider> list = new ArrayList<>();
        VoiceMetrics metrics = new VoiceMetrics(new SimpleMeterRegistry());

        // Gemini
        String geminiKey = System.getenv("GEMINI_API_KEY");
        if (geminiKey != null && !geminiKey.isBlank()) {
            try {
                GeminiSttProperties geminiProps = new GeminiSttProperties(geminiKey, null, "gemini-3.5-transcribe", 16000, 10);
                SttProperties props = new SttProperties(true, "gemini", language, List.of(), null, null, 0,
                        geminiProps, null, null, null, null);
                GeminiSttProvider gemini = new GeminiSttProvider(props, metrics);
                gemini.init();
                list.add(gemini);
            } catch (Exception e) {
                log.warn("Failed to init Gemini STT: {}", e.getMessage());
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
                SttProperties props = new SttProperties(true, "yandex", language, List.of(), null, null, 0,
                        null, null, yandexProps, null, null);
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
                SttProperties props = new SttProperties(true, "aisha", language, List.of(), null, null, 0,
                        null, null, null, aishaProps, null);
                AishaSttProvider aisha = new AishaSttProvider(props, metrics);
                aisha.init();
                list.add(aisha);
            } catch (Exception e) {
                log.warn("Failed to init Aisha STT: {}", e.getMessage());
            }
        }

        return list;
    }

    private static void collectWavFiles(Path path, List<Path> into) {
        if (Files.isDirectory(path)) {
            try (Stream<Path> children = Files.list(path)) {
                children.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".wav"))
                        .sorted()
                        .forEach(into::add);
            } catch (Exception e) {
                log.warn("Failed to list files in {}: {}", path, e.getMessage());
            }
        } else if (path.toString().toLowerCase().endsWith(".wav")) {
            into.add(path);
        }
    }
}
