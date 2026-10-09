package uz.murodjon.robotcallv2.agent.turn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Scores a text end-of-turn model against the examples live calls labelled for it
 * ({@code TurnDatasetRecorder}), before the model is trusted with a language.
 *
 * <p><b>Why this exists.</b> {@code TEXT_TURN_LANGUAGES=uz-UZ} switches a model on for
 * every Uzbek call at once. The two ways it can be wrong cost different things: calling an
 * unfinished sentence finished cuts the caller off (only when early closing is on), calling
 * a finished one unfinished adds {@code max-extend-ms} of silence. This prints both, per
 * threshold, next to the word-rule baseline the calls run on today
 * ({@link TranscriptTurnCues}) — so "turn it on" is a comparison, not a hope.
 *
 * <p>Labels: {@code end} — the caller stayed quiet through the reopen grace;
 * {@code continue} — they spoke again inside it, so the close had cut a sentence.
 *
 * <p>Usage:
 * <pre>
 *   java -cp ... uz.murodjon.robotcallv2.agent.turn.TextTurnReplayTool &lt;jsonl-file-or-dir&gt; \
 *        --model=models/uz_turn.onnx --tokenizer=models/uz_turn_tokenizer.json \
 *        [--language=uz-UZ] [--early-threshold=0.9] [--max-cut-percent=2]
 * </pre>
 *
 * <p>Exits 1 when, at {@code --early-threshold}, more than {@code --max-cut-percent} of the
 * {@code continue} examples would have been closed early — the gate for enabling
 * {@code TEXT_TURN_EARLY_WAIT_MS}.
 */
public final class TextTurnReplayTool {

    private record Example(String text, boolean continued) {
    }

    private TextTurnReplayTool() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: TextTurnReplayTool <jsonl-file-or-dir> --model=<onnx> --tokenizer=<json> [options]");
            System.exit(1);
        }
        String model = option(args, "--model", null);
        String tokenizer = option(args, "--tokenizer", null);
        String language = option(args, "--language", "uz-UZ");
        float earlyThreshold = Float.parseFloat(option(args, "--early-threshold", "0.9"));
        double maxCutPercent = Double.parseDouble(option(args, "--max-cut-percent", "2"));
        if (model == null || tokenizer == null) {
            System.err.println("--model and --tokenizer are required");
            System.exit(1);
        }

        List<Example> examples = read(Path.of(args[0]), language);
        long continued = examples.stream().filter(Example::continued).count();
        long ended = examples.size() - continued;
        System.out.printf("%d examples (%s): %d end, %d continue%n", examples.size(), language, ended, continued);
        if (continued == 0 || ended == 0) {
            System.err.println("Both labels are needed to score anything");
            System.exit(1);
        }

        TextTurnDetector detector = new TextTurnDetector(new TextTurnProperties(true, model, tokenizer,
                List.of(language), 0.5f, 0, 0, earlyThreshold, 128));
        detector.init();
        if (!detector.available()) {
            System.err.println("Model did not load — see the log above");
            System.exit(1);
        }
        float[] scores = new float[examples.size()];
        for (int i = 0; i < examples.size(); i++) {
            scores[i] = detector.completion(examples.get(i).text());
        }
        detector.shutdown();

        printBaseline(examples, continued, ended);

        System.out.println();
        System.out.println("threshold  caught-continue  extended-end   (extend when p(end) < threshold)");
        for (float threshold = 0.1f; threshold < 0.95f; threshold += 0.1f) {
            int[] counts = count(examples, scores, threshold, false);
            System.out.printf(Locale.ROOT, "  %.1f      %6.1f%%         %6.1f%%%n", threshold,
                    100.0 * counts[0] / continued, 100.0 * counts[1] / ended);
        }

        System.out.println();
        System.out.println("early      closed-end       cut-continue  (close early when p(end) >= threshold)");
        double cutAtGate = -1;
        for (float threshold : new float[]{0.7f, 0.8f, 0.9f, 0.95f, 0.99f, earlyThreshold}) {
            int[] counts = count(examples, scores, threshold, true);
            double cut = 100.0 * counts[0] / continued;
            if (threshold == earlyThreshold) {
                cutAtGate = cut;
            }
            System.out.printf(Locale.ROOT, "  %.2f     %6.1f%%          %6.1f%%%n", threshold,
                    100.0 * counts[1] / ended, cut);
        }

        if (cutAtGate > maxCutPercent) {
            System.out.printf(Locale.ROOT, "%nFAIL: at %.2f the model would cut off %.1f%% of unfinished callers (limit %.1f%%)%n",
                    earlyThreshold, cutAtGate, maxCutPercent);
            System.exit(1);
        }
        System.out.printf(Locale.ROOT, "%nOK: at %.2f %.1f%% of unfinished callers would be cut off (limit %.1f%%)%n",
                earlyThreshold, cutAtGate, maxCutPercent);
    }

    /** What the calls run on today: the last-word rule, which extends on INCOMPLETE and shortens on COMPLETE. */
    private static void printBaseline(List<Example> examples, long continued, long ended) {
        int caught = 0;
        int cut = 0;
        int extended = 0;
        int shortened = 0;
        for (Example example : examples) {
            TurnCue cue = TranscriptTurnCues.judge(example.text());
            if (example.continued()) {
                caught += cue == TurnCue.INCOMPLETE ? 1 : 0;
                cut += cue == TurnCue.COMPLETE ? 1 : 0;
            } else {
                extended += cue == TurnCue.INCOMPLETE ? 1 : 0;
                shortened += cue == TurnCue.COMPLETE ? 1 : 0;
            }
        }
        System.out.printf(Locale.ROOT, "baseline (TranscriptTurnCues): caught-continue %.1f%%, extended-end %.1f%%, "
                        + "shortened-end %.1f%%, shortened-continue %.1f%%%n",
                100.0 * caught / continued, 100.0 * extended / ended,
                100.0 * shortened / ended, 100.0 * cut / continued);
    }

    /**
     * Unscored examples (-1) count as the safe answer each way: never extended, never closed.
     *
     * @return early: {cut-continue, closed-end}; otherwise {caught-continue, extended-end}
     */
    private static int[] count(List<Example> examples, float[] scores, float threshold, boolean early) {
        int onContinue = 0;
        int onEnd = 0;
        for (int i = 0; i < examples.size(); i++) {
            float p = scores[i];
            if (p < 0) {
                continue;
            }
            boolean hit = early ? p >= threshold : p < threshold;
            if (hit) {
                if (examples.get(i).continued()) {
                    onContinue++;
                } else {
                    onEnd++;
                }
            }
        }
        return new int[]{onContinue, onEnd};
    }

    private static List<Example> read(Path input, String language) throws IOException {
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(input)) {
            try (Stream<Path> stream = Files.list(input)) {
                stream.filter(p -> p.toString().endsWith(".jsonl")).sorted().forEach(files::add);
            }
        } else {
            files.add(input);
        }
        ObjectMapper objectMapper = new ObjectMapper();
        List<Example> examples = new ArrayList<>();
        for (Path file : files) {
            for (String line : Files.readAllLines(file)) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode row = objectMapper.readTree(line);
                if (!language.equalsIgnoreCase(row.path("language").asText())) {
                    continue;
                }
                String text = row.path("text").asText();
                if (!text.isBlank()) {
                    examples.add(new Example(text, "continue".equals(row.path("label").asText())));
                }
            }
        }
        return examples;
    }

    private static String option(String[] args, String name, String fallback) {
        for (String arg : args) {
            if (arg.startsWith(name + "=")) {
                return arg.substring(name.length() + 1);
            }
        }
        return fallback;
    }
}
