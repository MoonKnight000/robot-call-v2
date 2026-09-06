package uz.murodjon.robotcallv2.agent.turn;

import uz.murodjon.robotcallv2.agent.audio.Resampler;
import uz.murodjon.robotcallv2.agent.rtp.WavChannels;
import uz.murodjon.robotcallv2.agent.rtp.WavHeader;
import uz.murodjon.robotcallv2.agent.rtp.WavReader;
import uz.murodjon.robotcallv2.agent.vad.SileroVad;
import uz.murodjon.robotcallv2.agent.vad.VadProperties;
import uz.murodjon.robotcallv2.agent.vad.VadStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Turns recorded calls into a labelled end-of-utterance training set, so a turn detector
 * can be fine-tuned for Uzbek (VOICE-QUALITY-PLAN C.4).
 *
 * <p><b>Why this exists.</b> {@link SmartTurnDetector} decides whether a caller has
 * finished talking or is only pausing, and it is the difference between a 700 ms wait in
 * front of every reply and a 250 ms one. The published models do not cover uz-UZ and
 * cannot be made to: they were trained on languages this one does not resemble. What
 * they can be is fine-tuned, and the material for that is sitting in the recordings
 * already — every pause a caller took, and whether they turned out to be finished.
 *
 * <p><b>How the labels are made.</b> Not by hand. A stereo call recording says who spoke
 * when, and that answers the question directly: after the caller stops, either the bot
 * took the turn (so the caller <em>had</em> finished — {@code COMPLETE}) or the caller
 * started again with nobody having replied (so they had not — {@code INCOMPLETE}). The
 * plan's original rule was a fixed 1.5 s resume window; the bot channel is better,
 * because it is the same judgement a listener would make rather than a guess at a
 * threshold. Where neither holds — the recording ends, or the caller resumes after a
 * pause so long that something else clearly went wrong — nothing is emitted, because a
 * wrong label is worse than a missing one.
 *
 * <p><b>What comes out.</b> One 16 kHz mono clip per pause, each ending {@code --tail-ms}
 * after the caller went quiet, plus {@code manifest.jsonl}. That tail is not decoration:
 * at inference the model is asked for a verdict once the gate has already waited, so it
 * sees the trailing silence, and a training set cut flush at the last word would teach it
 * about audio it never receives. Keep {@code --tail-ms} equal to the endpointing wait
 * actually in force ({@code voice-agent.stt.vad-gating.post-roll-ms}).
 *
 * <p>Offline, no network, no provider keys — the same {@link SileroVad} and
 * {@link VadStream} the live call runs, so a pause this finds is a pause the call would
 * have found. Training itself happens elsewhere (the smart-turn repository's own scripts);
 * this produces the input for it and nothing more.
 *
 * <p>Usage:
 * <pre>
 *   java -cp ... uz.murodjon.robotcallv2.agent.turn.TurnDatasetTool &lt;wav-file-or-dir&gt; \
 *        --vad-model=models/silero_vad.onnx --out=corpus/turn \
 *        [--mix=spatial|stereo] [--window-ms=8000] [--tail-ms=700] \
 *        [--max-pause-ms=4000] [--min-speech-ms=300] [--bot-rms=300]
 * </pre>
 */
public final class TurnDatasetTool {

    /** RTP arrives in 20 ms frames; replaying in the same size keeps the VAD's arithmetic identical. */
    private static final int FRAME_MS = 20;

    /** The rate a call is recorded at, and the only one this reads. */
    private static final int CALL_RATE = 8000;

    /**
     * The cross-feed {@code WavRecorder} mixes in for SPATIAL_STEREO: left is
     * {@code caller + 0.35*bot}, right is {@code bot + 0.35*caller}. Inverting it matters
     * — at 35 % the bot's own voice is loud enough for the VAD to call it speech, which
     * would put a "caller pause" in the middle of the bot's sentence.
     */
    private static final double BLEED = 0.35;

    private TurnDatasetTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: TurnDatasetTool <wav-file-or-dir> --vad-model=<path> --out=<dir> [options]");
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
        if (settings.out == null || settings.out.isBlank()) {
            System.err.println("--out=<directory for clips and manifest.jsonl> is required");
            System.exit(1);
        }

        List<Path> files = collectWavFiles(input);
        if (files.isEmpty()) {
            System.out.println("No .wav files found in " + input);
            return;
        }

        Path outDir = Path.of(settings.out);
        Path clipDir = outDir.resolve("clips");
        Files.createDirectories(clipDir);

        System.out.println("=== TURN DATASET ===");
        System.out.printf("window=%dms tail=%dms max-pause=%dms min-speech=%dms mix=%s%n%n",
                settings.windowMs, settings.tailMs, settings.maxPauseMs, settings.minSpeechMs, settings.mix);

        List<String> manifest = new ArrayList<>();
        Counts totals = new Counts();
        for (Path file : files) {
            try {
                extract(file, settings, clipDir, manifest, totals);
            } catch (IOException e) {
                System.err.printf("%s skipped: %s%n", file.getFileName(), e.getMessage());
            }
        }

        Path manifestPath = outDir.resolve("manifest.jsonl");
        Files.write(manifestPath, manifest, StandardCharsets.UTF_8);

        System.out.printf("=== %d file(s): %d COMPLETE, %d INCOMPLETE, %d ambiguous (dropped) ===%n",
                files.size(), totals.complete, totals.incomplete, totals.dropped);
        System.out.println("manifest: " + manifestPath);
        reportBalance(totals);
    }

    /**
     * A set that is nearly all one label trains a model that always answers that label
     * and scores well doing it. Said here rather than left to be discovered after a
     * training run, which costs a day.
     */
    private static void reportBalance(Counts totals) {
        int labelled = totals.complete + totals.incomplete;
        if (labelled == 0) {
            System.out.println("WARNING: nothing was labelled — check --mix and --vad-model against a recording by ear");
            return;
        }
        int minority = Math.min(totals.complete, totals.incomplete);
        int percent = minority * 100 / labelled;
        if (percent < 25) {
            System.out.printf("WARNING: the set is %d%% minority class — balance it before fine-tuning%n", percent);
        }
        if (labelled < 2000) {
            System.out.printf("NOTE: %d clips. A fine-tune wants several thousand; keep collecting.%n", labelled);
        }
    }

    /**
     * Find every caller pause in one recording, label the ones that can be labelled, and
     * write a clip for each.
     */
    private static void extract(Path file, Settings settings, Path clipDir,
                                List<String> manifest, Counts totals) throws IOException {
        WavChannels wav = WavReader.readChannels(file);
        if (wav.sampleRate() != CALL_RATE) {
            throw new IOException(wav.sampleRate() + " Hz, expected " + CALL_RATE);
        }
        short[] caller = callerChannel(wav, settings.mix);
        short[] bot = botChannel(wav, settings.mix);

        List<Run> callerRuns = speechRuns(caller, file.getFileName().toString(), settings);
        List<Run> botRuns = bot == null ? List.of() : energyRuns(bot, settings.botRms);
        long recordingMs = (long) caller.length * 1000 / CALL_RATE;

        int emitted = 0;
        for (int i = 0; i < callerRuns.size(); i++) {
            Run run = callerRuns.get(i);
            if (run.endMs - run.startMs < settings.minSpeechMs) {
                // Too short to be an utterance the model could learn anything from: a
                // cough, a "ha" clipped by the gate, a door. Kept out of both classes.
                continue;
            }
            if (spokeAt(botRuns, run.endMs)) {
                // The bot was already talking when the caller stopped: they were talking
                // over each other, and whatever the bot says next is the sentence it was
                // already in, not a reply. It is also a moment the live model never sees
                // — the gate is closed while the bot holds the floor — so training on it
                // teaches a situation that cannot arise.
                totals.dropped++;
                continue;
            }
            long resumeMs = i + 1 < callerRuns.size() ? callerRuns.get(i + 1).startMs : -1;
            long until = resumeMs < 0 ? recordingMs : resumeMs;
            // Began in the gap, not merely overlapping it. Overlap was the first version
            // and it was wrong in the one direction that matters: it counted the bot's
            // ongoing speech as a reply and labelled the caller's mid-sentence pauses
            // COMPLETE — six of them closed in under 700 ms, which is less than the wait
            // before a reply can even be synthesized.
            boolean botSpoke = startedBetween(botRuns, run.endMs, until);

            String label;
            if (botSpoke) {
                label = "COMPLETE";
            } else if (resumeMs < 0) {
                // Nothing follows. The caller may have finished and been hung up on, or
                // the recording may simply stop here — indistinguishable, so dropped.
                totals.dropped++;
                continue;
            } else if (until - run.endMs > settings.maxPauseMs) {
                // A long silence with no reply is not a turn boundary, it is a fault —
                // recognition dropped, the bot stalled. Labelling it either way teaches
                // the model about an outage.
                totals.dropped++;
                continue;
            } else {
                label = "INCOMPLETE";
            }

            String name = clipName(file, run.endMs, label);
            writeClip(clipDir.resolve(name), caller, run.endMs, settings);
            manifest.add(manifestLine(name, label, file, run, until, botSpoke));
            if ("COMPLETE".equals(label)) {
                totals.complete++;
            } else {
                totals.incomplete++;
            }
            emitted++;
        }
        System.out.printf("%s (%.1fs) — %d caller run(s), %d clip(s)%n",
                file.getFileName(), recordingMs / 1000d, callerRuns.size(), emitted);
    }

    /**
     * Where the caller spoke, found with the same VAD the live call uses.
     *
     * <p>The barge-in callback answers "yes, that interrupted something" every time, which
     * is what makes it usable as a start marker: {@link VadStream} disarms itself after a
     * true and does not re-arm until the silence reset, so it reports one onset per run of
     * speech rather than one per window. Answering false there would reset the counter and
     * fire again a few windows later, and the runs would come back shredded.
     */
    private static List<Run> speechRuns(short[] pcm, String channelId, Settings settings) {
        VadProperties props = new VadProperties(true, settings.vadModel, CALL_RATE,
                settings.windowSamples, settings.vadThreshold, settings.vadMinSpeechMs,
                settings.vadSilenceResetMs, null, 0.35f, 150.0, 2.5);
        SileroVad vad = new SileroVad(props);
        vad.init();
        if (!vad.available()) {
            System.err.println("VAD model could not be loaded: " + settings.vadModel);
            System.exit(1);
        }

        List<Long> onsets = new ArrayList<>();
        List<Long> ends = new ArrayList<>();
        long[] clock = {0};
        VadStream stream = new VadStream(vad, props, channelId,
                () -> {
                    // Confirmed only after minSpeechMs of speech, so the sound itself
                    // started that much earlier — and that is where the clip's audio has
                    // to begin for the run's length to mean anything.
                    onsets.add(Math.max(0, clock[0] - settings.vadMinSpeechMs));
                    return true;
                },
                null, null,
                waitMs -> ends.add(Math.max(0, clock[0] - waitMs)));

        int frame = Math.max(1, CALL_RATE * FRAME_MS / 1000);
        short[] chunk = new short[frame];
        for (int offset = 0; offset < pcm.length; offset += frame) {
            int length = Math.min(frame, pcm.length - offset);
            System.arraycopy(pcm, offset, chunk, 0, length);
            clock[0] = (long) (offset + length) * 1000 / CALL_RATE;
            stream.onAudio(chunk, length);
        }
        vad.shutdown();

        // An onset with no end is speech still running when the recording stopped; it has
        // no pause to label and is dropped by the pairing.
        List<Run> runs = new ArrayList<>();
        for (int i = 0; i < ends.size() && i < onsets.size(); i++) {
            runs.add(new Run(onsets.get(i), ends.get(i)));
        }
        return runs;
    }

    /**
     * Where the bot spoke, by energy rather than by VAD: this channel is synthesized
     * speech played straight down it, so there is no noise to reject and nothing a
     * neural detector would settle that a threshold does not. It also keeps the run to
     * one ONNX session.
     */
    private static List<Run> energyRuns(short[] pcm, double rmsThreshold) {
        int frame = Math.max(1, CALL_RATE * FRAME_MS / 1000);
        // Gaps shorter than this are inside one sentence — the stops between words, and
        // the pauses TTS puts at commas. Merging them keeps "the bot was talking here"
        // from coming back as forty separate runs.
        long mergeGapMs = 300;

        List<Run> runs = new ArrayList<>();
        long runStart = -1;
        long lastLoud = -1;
        for (int offset = 0; offset < pcm.length; offset += frame) {
            int length = Math.min(frame, pcm.length - offset);
            double sumSq = 0;
            for (int i = offset; i < offset + length; i++) {
                sumSq += (double) pcm[i] * pcm[i];
            }
            boolean loud = Math.sqrt(sumSq / length) >= rmsThreshold;
            long atMs = (long) offset * 1000 / CALL_RATE;
            if (loud) {
                if (runStart < 0) {
                    runStart = atMs;
                } else if (atMs - lastLoud > mergeGapMs) {
                    runs.add(new Run(runStart, lastLoud));
                    runStart = atMs;
                }
                lastLoud = atMs + FRAME_MS;
            }
        }
        if (runStart >= 0) {
            runs.add(new Run(runStart, lastLoud));
        }
        return runs;
    }

    /** Whether the bot <em>began</em> a run of speech in {@code [fromMs, toMs)} — a reply. */
    private static boolean startedBetween(List<Run> runs, long fromMs, long toMs) {
        for (Run run : runs) {
            if (run.startMs >= fromMs && run.startMs < toMs) {
                return true;
            }
        }
        return false;
    }

    /** Whether the bot was in the middle of talking at {@code atMs}. */
    private static boolean spokeAt(List<Run> runs, long atMs) {
        for (Run run : runs) {
            if (run.startMs <= atMs && atMs < run.endMs) {
                return true;
            }
        }
        return false;
    }

    /**
     * The caller's own voice. For SPATIAL_STEREO the two channels are a mix of both
     * speakers and have to be solved for one of them; for plain STEREO the left channel
     * already is the caller, and subtracting anything would only add artefacts.
     */
    private static short[] callerChannel(WavChannels wav, String mix) {
        if (wav.count() < 2) {
            return wav.channels()[0];
        }
        if (!"spatial".equals(mix)) {
            return wav.channels()[0];
        }
        return unmix(wav.channels()[0], wav.channels()[1]);
    }

    /** The bot's voice, or null for a mono recording that never carried it. */
    private static short[] botChannel(WavChannels wav, String mix) {
        if (wav.count() < 2) {
            return null;
        }
        if (!"spatial".equals(mix)) {
            return wav.channels()[1];
        }
        return unmix(wav.channels()[1], wav.channels()[0]);
    }

    /**
     * Solve {@code wanted = (a - BLEED*b) / (1 - BLEED^2)}, the inverse of the cross-feed
     * the recorder applied.
     */
    private static short[] unmix(short[] a, short[] b) {
        double scale = 1 / (1 - BLEED * BLEED);
        short[] out = new short[a.length];
        for (int i = 0; i < a.length; i++) {
            double value = (a[i] - BLEED * b[i]) * scale;
            out[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(value)));
        }
        return out;
    }

    /**
     * The model's window of audio ending {@code tailMs} after the caller stopped, at the
     * rate the model reads. Padded with silence at the front when the call is younger
     * than the window — which is what the ring buffer feeding it at runtime also holds.
     */
    private static void writeClip(Path path, short[] caller, long pauseEndMs, Settings settings) throws IOException {
        int windowSamples = settings.windowMs * CALL_RATE / 1000;
        short[] window = new short[windowSamples];
        long endMs = pauseEndMs + settings.tailMs;
        long startMs = endMs - settings.windowMs;

        int sourceStart = (int) (startMs * CALL_RATE / 1000);
        for (int i = 0; i < windowSamples; i++) {
            int source = sourceStart + i;
            if (source >= 0 && source < caller.length) {
                window[i] = caller[source];
            }
        }

        short[] upsampled = Resampler.upsample8kTo16k(window, window.length);
        byte[] data = new byte[upsampled.length * 2];
        for (int i = 0; i < upsampled.length; i++) {
            data[i * 2] = (byte) (upsampled[i] & 0xFF);
            data[i * 2 + 1] = (byte) ((upsampled[i] >> 8) & 0xFF);
        }
        try (OutputStream out = Files.newOutputStream(path)) {
            out.write(WavHeader.bytes(SmartTurnDetector.SAMPLE_RATE, 1, data.length));
            out.write(data);
        }
    }

    private static String clipName(Path source, long pauseEndMs, String label) {
        String stem = source.getFileName().toString().replaceFirst("\\.[wW][aA][vV]$", "");
        return stem.replaceAll("[^A-Za-z0-9_.-]", "_") + "_" + pauseEndMs + "ms_" + label.toLowerCase() + ".wav";
    }

    /**
     * One JSON object per clip. Written by hand rather than through a mapper because this
     * runs from a bare classpath, and every value here is a number or a name this tool
     * made itself.
     */
    private static String manifestLine(String name, String label, Path source, Run run,
                                       long until, boolean botSpoke) {
        return "{\"file\":\"clips/" + name + "\""
                + ",\"label\":\"" + label + "\""
                + ",\"source\":\"" + escape(source.getFileName().toString()) + "\""
                + ",\"speech_start_ms\":" + run.startMs
                + ",\"pause_start_ms\":" + run.endMs
                + ",\"pause_ms\":" + (until - run.endMs)
                + ",\"bot_replied\":" + botSpoke
                + "}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static List<Path> collectWavFiles(Path input) throws IOException {
        if (Files.isRegularFile(input)) {
            return List.of(input);
        }
        try (Stream<Path> walk = Files.walk(input)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".wav"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    /** One run of speech on one channel. */
    private record Run(long startMs, long endMs) {
    }

    private static final class Counts {
        private int complete;
        private int incomplete;
        private int dropped;
    }

    private static final class Settings {
        private String vadModel = System.getenv("VAD_MODEL_PATH");
        private String out;
        private String mix = "spatial";

        private int windowSamples = 256;
        private float vadThreshold = 0.5f;
        private int vadMinSpeechMs = 180;
        private int vadSilenceResetMs = 250;

        /** Smart Turn v3 scores 8 s of audio; a clip of another length cannot be fed to it. */
        private int windowMs = 8000;
        /**
         * Trailing silence the model sees at inference, and so the silence every clip
         * ends with. Tracks {@code voice-agent.stt.vad-gating.post-roll-ms} — the two
         * move together, and a corpus cut at the old wait has to be regenerated when
         * that one changes.
         */
        private int tailMs = 1200;
        /** Beyond this a silence with no reply is a fault rather than a turn boundary. */
        private int maxPauseMs = 4000;
        /** Shorter runs of speech are noise, not utterances. */
        private int minSpeechMs = 300;
        /** RMS above which the bot channel counts as talking. */
        private double botRms = 300;

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
                    case "out" -> settings.out = value;
                    case "mix" -> settings.mix = value;
                    case "window-samples" -> settings.windowSamples = Integer.parseInt(value);
                    case "vad-threshold" -> settings.vadThreshold = Float.parseFloat(value);
                    case "vad-min-speech-ms" -> settings.vadMinSpeechMs = Integer.parseInt(value);
                    case "vad-silence-reset-ms" -> settings.vadSilenceResetMs = Integer.parseInt(value);
                    case "window-ms" -> settings.windowMs = Integer.parseInt(value);
                    case "tail-ms" -> settings.tailMs = Integer.parseInt(value);
                    case "max-pause-ms" -> settings.maxPauseMs = Integer.parseInt(value);
                    case "min-speech-ms" -> settings.minSpeechMs = Integer.parseInt(value);
                    case "bot-rms" -> settings.botRms = Double.parseDouble(value);
                    default -> System.err.println("Unknown option ignored: --" + name);
                }
            }
            return settings;
        }
    }
}
