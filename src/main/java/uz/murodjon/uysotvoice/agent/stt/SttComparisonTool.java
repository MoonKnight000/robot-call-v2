package uz.murodjon.uysotvoice.agent.stt;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.LoggerFactory;

import uz.murodjon.uysotvoice.agent.audio.Resampler;
import uz.murodjon.uysotvoice.agent.metrics.VoiceMetrics;
import uz.murodjon.uysotvoice.agent.rtp.WavAudio;
import uz.murodjon.uysotvoice.agent.rtp.WavReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Offline A/B comparison of the STT providers on recorded calls — which provider hears
 * Uzbek better is decided by reading transcripts of the same audio side by side, not by
 * opinion. Runs without Spring: a {@code main} started from the IDE that feeds each
 * recording's caller channel (call recordings are stereo, caller left / bot right —
 * {@link WavReader} already extracts the left channel) to every provider that has
 * credentials, and prints their final transcripts under each other.
 *
 * <p>Usage: run this class with the working directory at the project root. Arguments are
 * WAV files or directories of them; no arguments means the {@code recordings/} directory.
 * {@code --language=ru-RU} overrides the recognition language (default {@code uz-UZ}).
 *
 * <p>Credentials come from the same environment variables the app uses: {@code
 * STT_YANDEX_API_KEY} (plus optional {@code STT_YANDEX_FOLDER_ID}/{@code
 * STT_YANDEX_MODEL}) for Yandex, {@code GOOGLE_APPLICATION_CREDENTIALS} (plus optional
 * {@code STT_MODEL}) for Google, {@code STT_AISHA_API_KEY} for Aisha. A provider with no
 * credentials is skipped, so the tool
 * is still useful with only one configured — it then just transcribes.
 *
 * <p>Audio is paced at real time and both providers listen to the same pass
 * simultaneously, so a comparison takes as long as the recording lasts. Yandex's
 * REAL_TIME processing type expects that pacing; pushing faster risks skewed results.
 * Endpointing is left to each provider (no external EOU) — utterance splits then show
 * where each provider's own detector cuts, which is part of what is being compared.
 */
public final class SttComparisonTool {

    private static final int TELEPHONE_RATE = 8000;
    private static final int CHUNK_MS = 100;
    /** How long after the audio ends the providers get to deliver their last finals. */
    private static final long FINALIZE_WAIT_MS = 3000;

    private SttComparisonTool() {
    }

    public static void main(String[] args) throws Exception {
        // Outside Spring, Logback falls back to a DEBUG root logger and gRPC/netty
        // drown the transcripts. The providers' own INFO lines stay visible.
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);

        String language = readEnv("STT_DEFAULT_LANGUAGE", "uz-UZ");
        List<Path> files = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith("--language=")) {
                language = arg.substring("--language=".length());
            } else {
                collectWavFiles(Path.of(arg), files);
            }
        }
        if (files.isEmpty()) {
            collectWavFiles(Path.of("recordings"), files);
        }
        if (files.isEmpty()) {
            System.err.println("No WAV files found. Pass files/directories as arguments, "
                    + "or record a call first (recordings/).");
            System.exit(1);
        }

        VoiceMetrics metrics = new VoiceMetrics(new SimpleMeterRegistry());
        YandexSttProvider yandex = buildYandex(language, metrics);
        GoogleSttProvider google = buildGoogle(language, metrics);
        AishaSttProvider aisha = buildAisha(language, metrics);
        Map<String, SttProvider> providers = new LinkedHashMap<>();
        if (yandex != null) {
            providers.put("yandex", yandex);
        }
        if (google != null) {
            providers.put("google", google);
        }
        if (aisha != null) {
            providers.put("aisha", aisha);
        }
        if (providers.isEmpty()) {
            System.err.println("No STT credentials found. Set STT_YANDEX_API_KEY, "
                    + "STT_AISHA_API_KEY and/or GOOGLE_APPLICATION_CREDENTIALS in the run environment.");
            System.exit(1);
        }
        System.out.printf("Comparing %d file(s) in %s with: %s%n%n",
                files.size(), language, String.join(", ", providers.keySet()));

        for (Path file : files) {
            compareOn(file, language, providers);
        }

        if (yandex != null) {
            yandex.shutdown();
        }
        if (google != null) {
            google.shutdown();
        }
        // Both clients keep worker threads that outlive their shutdown grace period.
        System.exit(0);
    }

    /** Feed one recording to every provider at real time and print what each heard. */
    private static void compareOn(Path file, String language, Map<String, SttProvider> providers)
            throws InterruptedException {
        WavAudio audio;
        try {
            audio = WavReader.read(file);
        } catch (IOException e) {
            System.err.printf("skip %s: %s%n", file, e.getMessage());
            return;
        }
        if (audio.sampleRate() != TELEPHONE_RATE) {
            // Call recordings are always 8 kHz; anything else is not one of ours and the
            // providers below were configured for the telephone rate.
            System.err.printf("skip %s: %d Hz, expected %d%n", file, audio.sampleRate(), TELEPHONE_RATE);
            return;
        }
        byte[] pcm = toLittleEndianBytes(audio.samples());
        // Aisha only accepts a 16 kHz stream, so the same pass is prepared at both rates
        // and each provider is fed the one it asked for — exactly what the live pipeline
        // does (SttStreamBridge).
        byte[] pcmWide = toLittleEndianBytes(Resampler.upsample8kTo16k(audio.samples(), audio.samples().length));
        System.out.printf("=== %s — %.1fs ===%n",
                file, audio.samples().length / (double) TELEPHONE_RATE);

        Map<String, FinalCollector> collectors = new LinkedHashMap<>();
        Map<String, SttSession> sessions = new LinkedHashMap<>();
        for (Map.Entry<String, SttProvider> entry : providers.entrySet()) {
            FinalCollector collector = new FinalCollector();
            try {
                sessions.put(entry.getKey(), entry.getValue().startStream(language, collector, false));
                collectors.put(entry.getKey(), collector);
            } catch (Exception e) {
                System.err.printf("[%s] stream failed to open: %s%n", entry.getKey(), e.getMessage());
            }
        }
        if (sessions.isEmpty()) {
            return;
        }

        int chunkBytes = CHUNK_MS * TELEPHONE_RATE / 1000 * 2;
        for (int offset = 0; offset < pcm.length; offset += chunkBytes) {
            byte[] chunk = Arrays.copyOfRange(pcm, offset, Math.min(offset + chunkBytes, pcm.length));
            byte[] wideChunk = Arrays.copyOfRange(pcmWide, offset * 2,
                    Math.min(offset * 2 + chunkBytes * 2, pcmWide.length));
            for (Map.Entry<String, SttSession> entry : sessions.entrySet()) {
                boolean wide = providers.get(entry.getKey()).sampleRate() == 16000;
                entry.getValue().sendAudio(wide ? wideChunk : chunk);
            }
            Thread.sleep(CHUNK_MS);
        }
        for (SttSession session : sessions.values()) {
            try {
                session.close();
            } catch (IOException e) {
                // The stream already died server-side; its finals (if any) are collected.
            }
        }
        Thread.sleep(FINALIZE_WAIT_MS);

        for (Map.Entry<String, FinalCollector> entry : collectors.entrySet()) {
            List<String> finals = entry.getValue().finals();
            System.out.printf("[%s]%n", entry.getKey());
            if (finals.isEmpty()) {
                System.out.println("    (no transcript)");
            } else {
                finals.forEach(line -> System.out.println("    " + line));
            }
        }
        System.out.println();
    }

    /** A Yandex provider from the environment, or {@code null} without an API key. */
    private static YandexSttProvider buildYandex(String language, VoiceMetrics metrics) {
        String apiKey = readEnv("STT_YANDEX_API_KEY", "");
        if (apiKey.isBlank()) {
            return null;
        }
        YandexSttProperties yandexProperties = new YandexSttProperties(
                apiKey,
                readEnv("STT_YANDEX_FOLDER_ID", ""),
                readEnv("STT_YANDEX_HOST", "stt.api.cloud.yandex.net"),
                443,
                TELEPHONE_RATE,
                readEnv("STT_YANDEX_MODEL", "general"),
                false, // finals only — interims would just interleave in the output
                EouSensitivity.DEFAULT,
                0,
                0); // one-shot tool: no idle connection to keep alive
        YandexSttProvider provider = new YandexSttProvider(
                buildSttProperties(language, null, yandexProperties, null), metrics);
        provider.init();
        return provider;
    }

    /** An Aisha provider from the environment, or {@code null} without an API key. */
    private static AishaSttProvider buildAisha(String language, VoiceMetrics metrics) {
        String apiKey = readEnv("STT_AISHA_API_KEY", "");
        if (apiKey.isBlank()) {
            return null;
        }
        AishaSttProperties aishaProperties = new AishaSttProperties(
                apiKey,
                readEnv("STT_AISHA_URL", "wss://back.aisha.group/api/v1/stt/realtime"),
                false); // finals only — interims would just interleave in the output
        AishaSttProvider provider = new AishaSttProvider(
                buildSttProperties(language, null, null, aishaProperties), metrics);
        provider.init();
        return provider;
    }

    /** A Google provider from the environment, or {@code null} without credentials. */
    private static GoogleSttProvider buildGoogle(String language, VoiceMetrics metrics) {
        if (readEnv("GOOGLE_APPLICATION_CREDENTIALS", "").isBlank()) {
            return null;
        }
        GoogleSttProperties googleProperties = new GoogleSttProperties(
                readEnv("STT_MODEL", ""), TELEPHONE_RATE, true, 600);
        GoogleSttProvider provider = new GoogleSttProvider(
                buildSttProperties(language, googleProperties, null, null), metrics);
        provider.init();
        return provider;
    }

    /**
     * The provider-facing slice of {@link SttProperties}. Gating/endpointing are the
     * live pipeline's concern and stay null — the providers never read them.
     */
    private static SttProperties buildSttProperties(String language, GoogleSttProperties google,
                                                    YandexSttProperties yandex, AishaSttProperties aisha) {
        String provider = yandex != null ? "yandex" : (aisha != null ? "aisha" : "google");
        return new SttProperties(true, provider, language, null, null, google, yandex, aisha);
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
            System.err.printf("not found: %s%n", path);
        }
    }

    private static byte[] toLittleEndianBytes(short[] samples) {
        byte[] bytes = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            bytes[i * 2] = (byte) (samples[i] & 0xFF);
            bytes[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
        }
        return bytes;
    }

    private static String readEnv(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Collects the final transcripts one provider produced, in arrival order. */
    private static final class FinalCollector implements TranscriptListener {

        private final List<String> lines = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void onTranscript(String text, boolean isFinal, float confidence) {
            if (isFinal && text != null && !text.isBlank()) {
                lines.add(text.trim());
            }
        }

        List<String> finals() {
            return List.copyOf(lines);
        }
    }
}
