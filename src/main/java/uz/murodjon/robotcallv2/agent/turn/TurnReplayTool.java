package uz.murodjon.robotcallv2.agent.turn;

import uz.murodjon.robotcallv2.agent.audio.SpeechGate;
import uz.murodjon.robotcallv2.agent.rtp.WavAudio;
import uz.murodjon.robotcallv2.agent.rtp.WavReader;
import uz.murodjon.robotcallv2.agent.vad.SileroVad;
import uz.murodjon.robotcallv2.agent.vad.VadProperties;
import uz.murodjon.robotcallv2.agent.vad.VadStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Replays recorded call audio through the live VAD, gate and turn detector, and prints
 * where each caller turn was closed and how much silence it cost.
 *
 * <p><b>Why this exists.</b> Every endpointing setting is a trade between two failures
 * that never show up in the same call: too long and the caller waits through a second of
 * silence on every turn, too short and their sentence is cut in half and answered as two.
 * Tuning it on live calls means discovering the second failure on a customer. This runs
 * the same code — {@link SileroVad}, {@link VadStream}, {@link SpeechGate},
 * {@link SmartTurnDetector} — over a recording, offline and deterministically, so the
 * effect of a change is a diff rather than a hope.
 *
 * <p>No network and no provider keys: this measures <em>where the turns are</em>, not what
 * was said. That is the part a config change moves, and the part
 * {@code SttComparisonTool} does not cover.
 *
 * <p>Usage:
 * <pre>
 *   java -cp ... uz.murodjon.robotcallv2.agent.turn.TurnReplayTool &lt;wav-file-or-dir&gt; \
 *        --vad-model=models/silero_vad.onnx \
 *        [--post-roll-ms=1000] [--short-silence-ms=500] [--short-utterance-ms=1500] \
 *        [--min-speech-ms=80] [--pre-roll-ms=800] \
 *        [--dynamic-min-post-roll-ms=0] [--ema-alpha=0.2] [--reopen-grace-ms=900] \
 *        [--turn-model=models/smart_turn_v3.onnx] [--turn-threshold=0.5] [--turn-extend-ms=500] \
 *        [--early-wait-ms=0] [--early-threshold=0.9] \
 *        [--expect=N] [--manifest=corpus/manifest.tsv]
 * </pre>
 *
 * <p>{@code --expect} makes it a test: the run fails (exit 1) when a file does not close
 * exactly that many turns. One number covers a corpus where every recording has the same
 * shape, which no real corpus does — {@code --manifest} gives each file its own count:
 *
 * <pre>
 *   # filename                 expected turns
 *   2026-08-14-promise.wav     4
 *   2026-08-14-refusal.wav     3
 * </pre>
 *
 * <p>Tab- or space-separated, {@code #} comments and blank lines ignored, paths relative
 * to the manifest's own directory. Files the manifest does not mention are replayed and
 * reported but cannot fail the run — the corpus grows faster than anybody labels it.
 */
public final class TurnReplayTool {

    /** RTP arrives in 20 ms frames; replaying in the same size keeps the gate's arithmetic identical. */
    private static final int FRAME_MS = 20;

    private TurnReplayTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: TurnReplayTool <wav-file-or-dir> --vad-model=<path> [options]");
            System.exit(1);
        }
        Path input = Path.of(args[0]);
        if (!Files.exists(input)) {
            System.err.println("Not found: " + input);
            System.exit(1);
        }
        Settings settings = Settings.parse(args);
        if (settings.vadModel == null || settings.vadModel.isBlank()) {
            System.err.println("--vad-model=<path to silero_vad.onnx> is required");
            System.exit(1);
        }

        List<Path> files = collectWavFiles(input);
        if (files.isEmpty()) {
            System.out.println("No .wav files found in " + input);
            return;
        }

        System.out.println("=== TURN REPLAY ===");
        System.out.printf("post-roll=%dms short-silence=%dms short-utterance=%dms min-speech=%dms%n",
                settings.postRollMs, settings.shortSilenceMs, settings.shortUtteranceMs, settings.minSpeechMs);
        if (settings.dynamicMinPostRollMs > 0) {
            System.out.printf("adaptive: floor=%dms alpha=%.2f grace=%dms%n",
                    settings.dynamicMinPostRollMs, settings.emaAlpha, settings.reopenGraceMs);
        }
        if (settings.turnModel != null && !settings.turnModel.isBlank()) {
            System.out.printf("smart turn: threshold=%.2f max-extend=%dms%n",
                    settings.turnThreshold, settings.turnExtendMs);
        }
        System.out.println();

        Map<String, Integer> expected = loadManifest(settings.manifest);

        int checked = 0;
        int failures = 0;
        for (Path file : files) {
            int expect = expected.getOrDefault(file.getFileName().toString(), settings.expectedTurns);
            if (expect >= 0) {
                checked++;
            }
            failures += replay(file, settings, expect) ? 1 : 0;
        }
        System.out.printf("=== %d file(s), %d checked, %d failed ===%n",
                files.size(), checked, failures);
        if (failures > 0) {
            System.exit(1);
        }
    }

    /**
     * Expected turn counts per recording, keyed by file name.
     *
     * <p>Read here rather than passed on the command line because a corpus is labelled
     * once and replayed on every change; a list of thirty numbers in a CI file would go
     * stale the first time somebody added a recording.
     */
    private static Map<String, Integer> loadManifest(String manifest) throws IOException {
        if (manifest == null || manifest.isBlank()) {
            return Map.of();
        }
        Path path = Path.of(manifest);
        if (!Files.exists(path)) {
            System.err.println("Manifest not found: " + path);
            System.exit(1);
        }
        Map<String, Integer> expected = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            if (parts.length < 2) {
                System.err.println("Ignoring malformed manifest line: " + trimmed);
                continue;
            }
            try {
                expected.put(Path.of(parts[0]).getFileName().toString(), Integer.parseInt(parts[1]));
            } catch (NumberFormatException e) {
                System.err.println("Ignoring manifest line with a non-numeric count: " + trimmed);
            }
        }
        System.out.printf("manifest: %d labelled recording(s)%n%n", expected.size());
        return expected;
    }

    /**
     * @param expect turns this recording must close, or {@code -1} to report only
     * @return whether this file failed that check
     */
    private static boolean replay(Path file, Settings settings, int expect) throws IOException {
        WavAudio audio = WavReader.read(file);
        int rate = audio.sampleRate();
        SpeechGate gate = new SpeechGate(rate, settings.preRollMs, settings.postRollMs, settings.minSpeechMs,
                settings.shortUtteranceMs, settings.shortSilenceMs,
                settings.dynamicMinPostRollMs, settings.emaAlpha, settings.reopenGraceMs);

        SileroVad vad = new SileroVad(new VadProperties(true, settings.vadModel, rate,
                settings.windowSamples, settings.vadThreshold, settings.vadMinSpeechMs,
                settings.vadSilenceResetMs, null, 0.35f, 150.0, 2.5));
        vad.init();
        if (!vad.available()) {
            System.err.println("VAD model could not be loaded: " + settings.vadModel);
            System.exit(1);
        }

        UtteranceBuffer buffer = null;
        SmartTurnDetector detector = settings.turnModel == null || settings.turnModel.isBlank()
                ? null
                : loadDetector(settings);
        if (detector != null) {
            if (rate != UtteranceBuffer.sourceRate()) {
                // The buffer resamples from the call's rate to the model's; fed anything
                // else it would score a spectrogram of audio at the wrong speed.
                System.err.printf("Smart Turn skipped for %s: %d Hz, expected %d Hz%n",
                        file.getFileName(), rate, UtteranceBuffer.sourceRate());
                detector = null;
            }
        }
        if (detector != null) {
            buffer = new UtteranceBuffer(detector.samples());
            UtteranceBuffer scored = buffer;
            SmartTurnDetector loaded = detector;
            gate.setTurnDetector(() -> loaded.isComplete(scored.recent(), scored.length()), settings.turnExtendMs);
            if (settings.earlyWaitMs > 0) {
                gate.setEarlyClose(() -> loaded.isConfidentlyComplete(scored.recent(), scored.length()),
                        settings.earlyWaitMs);
            }
        }

        // The barge-in callback answers "nothing was interrupted", which is what a replay
        // of the caller's side is: there is no bot talking over them here.
        VadStream stream = new VadStream(vad, new VadProperties(true, settings.vadModel, rate,
                settings.windowSamples, settings.vadThreshold, settings.vadMinSpeechMs,
                settings.vadSilenceResetMs, null, 0.35f, 150.0, 2.5), file.getFileName().toString(), () -> false, gate, null, null);

        int frame = Math.max(1, rate * FRAME_MS / 1000);
        short[] samples = audio.samples();
        short[] chunk = new short[frame];
        List<Turn> turns = new ArrayList<>();
        boolean wasOpen = false;
        long openedAtMs = 0;

        for (int offset = 0; offset < samples.length; offset += frame) {
            int length = Math.min(frame, samples.length - offset);
            System.arraycopy(samples, offset, chunk, 0, length);
            if (buffer != null) {
                buffer.onAudio(chunk, length);
            }
            stream.onAudio(chunk, length);
            long atMs = (long) (offset + length) * 1000 / rate;
            boolean open = gate.isOpen();
            if (open && !wasOpen) {
                openedAtMs = atMs;
            } else if (!open && wasOpen) {
                int waited = gate.lastCloseWaitMs();
                turns.add(new Turn(openedAtMs, atMs - waited, waited));
            }
            wasOpen = open;
        }
        if (wasOpen) {
            // The recording ended mid-utterance: there is no close to measure, and
            // counting it as a turn would make the file's turn count depend on where the
            // recording was cut rather than on the settings under test.
            turns.add(new Turn(openedAtMs, (long) samples.length * 1000 / rate, -1));
        }

        System.out.printf("%s (%.1fs, %d Hz)%n", file.getFileName(),
                samples.length / (double) rate, rate);
        long totalWait = 0;
        int closed = 0;
        for (int i = 0; i < turns.size(); i++) {
            Turn turn = turns.get(i);
            if (turn.eouWaitMs >= 0) {
                totalWait += turn.eouWaitMs;
                closed++;
                System.out.printf("  turn %2d  %6.2fs - %6.2fs  speech %5.2fs  eou wait %4dms%n",
                        i + 1, turn.startMs / 1000d, turn.endMs / 1000d,
                        (turn.endMs - turn.startMs) / 1000d, turn.eouWaitMs);
            } else {
                System.out.printf("  turn %2d  %6.2fs - %6.2fs  speech %5.2fs  (unclosed at end of file)%n",
                        i + 1, turn.startMs / 1000d, turn.endMs / 1000d,
                        (turn.endMs - turn.startMs) / 1000d);
            }
        }
        System.out.printf("  => %d turn(s), average eou wait %dms%n%n",
                turns.size(), closed > 0 ? totalWait / closed : 0);

        if (expect >= 0 && turns.size() != expect) {
            System.out.printf("  FAIL: expected %d turn(s), got %d%n%n", expect, turns.size());
            return true;
        }
        return false;
    }

    private static SmartTurnDetector loadDetector(Settings settings) {
        SmartTurnProperties props = new SmartTurnProperties(true, settings.turnModel, List.of(),
                settings.turnThreshold, settings.turnExtendMs, settings.earlyWaitMs, settings.earlyThreshold,
                settings.nFft, settings.hopSamples, settings.mels, settings.frames);
        SmartTurnDetector detector = new SmartTurnDetector(props);
        detector.init();
        if (!detector.available()) {
            System.err.println("Smart Turn model could not be loaded: " + settings.turnModel);
            System.exit(1);
        }
        return detector;
    }

    private static List<Path> collectWavFiles(Path input) throws IOException {
        if (Files.isRegularFile(input)) {
            return List.of(input);
        }
        try (Stream<Path> paths = Files.walk(input)) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".wav"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    /** One closed caller turn: when it started, when it really ended, and what the close cost. */
    private record Turn(long startMs, long endMs, int eouWaitMs) {
    }

    /** Command-line settings, defaulted to what {@code config/speech.yml} ships. */
    private static final class Settings {
        private String vadModel = System.getenv("VAD_MODEL_PATH");
        private int windowSamples = 256;
        private float vadThreshold = 0.5f;
        private int vadMinSpeechMs = 180;
        private int vadSilenceResetMs = 250;

        private int preRollMs = 800;
        private int postRollMs = 1000;
        private int minSpeechMs = 80;
        private int shortUtteranceMs = 1500;
        private int shortSilenceMs = 500;
        private int dynamicMinPostRollMs = 0;
        private double emaAlpha = 0.2d;
        private int reopenGraceMs = 900;

        private String turnModel = System.getenv("TURN_MODEL_PATH");
        private float turnThreshold = 0.5f;
        private int turnExtendMs = 500;
        private int earlyWaitMs = 0;
        private float earlyThreshold = 0.9f;
        private int nFft = 512;
        private int hopSamples = 256;
        private int mels = 128;
        private int frames = 500;

        private int expectedTurns = -1;
        private String manifest = null;

        private static Settings parse(String[] args) {
            Settings settings = new Settings();
            for (String arg : args) {
                if (!arg.startsWith("--")) {
                    continue;
                }
                int eq = arg.indexOf('=');
                String name = eq < 0 ? arg.substring(2) : arg.substring(2, eq);
                String value = eq < 0 ? "" : arg.substring(eq + 1);
                switch (name) {
                    case "vad-model" -> settings.vadModel = value;
                    case "window-samples" -> settings.windowSamples = Integer.parseInt(value);
                    case "vad-threshold" -> settings.vadThreshold = Float.parseFloat(value);
                    case "vad-min-speech-ms" -> settings.vadMinSpeechMs = Integer.parseInt(value);
                    case "vad-silence-reset-ms" -> settings.vadSilenceResetMs = Integer.parseInt(value);
                    case "pre-roll-ms" -> settings.preRollMs = Integer.parseInt(value);
                    case "post-roll-ms" -> settings.postRollMs = Integer.parseInt(value);
                    case "min-speech-ms" -> settings.minSpeechMs = Integer.parseInt(value);
                    case "short-utterance-ms" -> settings.shortUtteranceMs = Integer.parseInt(value);
                    case "short-silence-ms" -> settings.shortSilenceMs = Integer.parseInt(value);
                    case "dynamic-min-post-roll-ms" -> settings.dynamicMinPostRollMs = Integer.parseInt(value);
                    case "ema-alpha" -> settings.emaAlpha = Double.parseDouble(value);
                    case "reopen-grace-ms" -> settings.reopenGraceMs = Integer.parseInt(value);
                    case "turn-model" -> settings.turnModel = value;
                    case "turn-threshold" -> settings.turnThreshold = Float.parseFloat(value);
                    case "turn-extend-ms" -> settings.turnExtendMs = Integer.parseInt(value);
                    case "early-wait-ms" -> settings.earlyWaitMs = Integer.parseInt(value);
                    case "early-threshold" -> settings.earlyThreshold = Float.parseFloat(value);
                    case "n-fft" -> settings.nFft = Integer.parseInt(value);
                    case "hop-samples" -> settings.hopSamples = Integer.parseInt(value);
                    case "mels" -> settings.mels = Integer.parseInt(value);
                    case "frames" -> settings.frames = Integer.parseInt(value);
                    case "expect" -> settings.expectedTurns = Integer.parseInt(value);
                    case "manifest" -> settings.manifest = value;
                    default -> System.err.println("Unknown option ignored: " + arg);
                }
            }
            return settings;
        }
    }
}
