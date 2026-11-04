package uz.murodjon.robotcallv2.agent.turn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Scores a TEN-style text turn detector — phase 0 of the "variant A" plan: a prompted fast
 * LLM (the agent's {@code fastLlmModel}) instead of TEN's fine-tuned Qwen, which knows no Uzbek.
 *
 * <p>What is copied, and from where:
 * <ul>
 *   <li>TEN Turn Detection: the caller's words with punctuation removed, one short answer
 *       out of three classes — {@code finished}, {@code unfinished}, {@code wait} (the caller
 *       asked the agent to hold on or be quiet) — and "unfinished" whenever the answer is
 *       missing or unreadable;</li>
 *   <li>LiveKit Turn Detector: the agent's last line goes in as context, and the verdict is a
 *       probability compared against a threshold. With {@code --logprobs} the tool reads
 *       p(finished) off the first answer token and sweeps the thresholds, as LiveKit tunes
 *       its per-language one.</li>
 * </ul>
 *
 * <p>Rows come from {@code TurnDatasetRecorder}: {@code end} — the caller let the agent
 * reply; {@code continue} — they went on talking. The costly error is {@code finished} on a
 * {@code continue} row (the bot talks over the caller); {@code unfinished}/{@code wait} on
 * an {@code end} row only adds silence.
 *
 * <p>Any OpenAI-compatible chat-completions endpoint works; the key is read from an
 * environment variable, never from the command line:
 * <pre>
 *   java -cp ... uz.murodjon.robotcallv2.agent.turn.LlmTurnDetectorReplayTool &lt;jsonl-file-or-dir&gt; \
 *        [--url=https://generativelanguage.googleapis.com/v1beta/openai/chat/completions] \
 *        [--model=gemini-3.5-flash-lite] [--api-key-env=GEMINI_API_KEY] [--language=uz-UZ] \
 *        [--max-tokens=8] [--reasoning-effort=none] [--logprobs] [--max-cut-percent=2] [--verbose]
 * </pre>
 * {@code --reasoning-effort} is passed through only when given: a thinking model may spend
 * {@code --max-tokens} on thought and answer nothing, which shows up as "no answer".
 *
 * <p>Exits 1 when more than {@code --max-cut-percent} of the {@code continue} rows were
 * judged {@code finished}.
 */
public final class LlmTurnDetectorReplayTool {

    /** TEN strips these before asking; the recognizer's punctuation is a guess, not a cue. */
    private static final Pattern PUNCTUATION = Pattern.compile("[,.!?:;…«»\"“”]");

    private static final double[] THRESHOLDS = {0.3, 0.5, 0.6, 0.7, 0.8, 0.9, 0.95};

    private record Example(String agentLine, String text, boolean continued) {
    }

    private enum Decision { FINISHED, UNFINISHED, WAIT, NONE }

    /** One answer: the decision and, with {@code --logprobs}, p(finished) — NaN when not readable. */
    private record Verdict(Decision decision, double finishedProbability, long latencyMs) {
    }

    private LlmTurnDetectorReplayTool() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 1) {
            System.err.println("Usage: LlmTurnDetectorReplayTool <jsonl-file-or-dir> [options]");
            System.exit(1);
        }
        String url = option(args, "--url", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions");
        String model = option(args, "--model", "gemini-3.5-flash-lite");
        String apiKey = System.getenv(option(args, "--api-key-env", "GEMINI_API_KEY"));
        String language = option(args, "--language", "uz-UZ");
        int maxTokens = Integer.parseInt(option(args, "--max-tokens", "8"));
        String reasoningEffort = option(args, "--reasoning-effort", null);
        boolean logprobs = List.of(args).contains("--logprobs");
        double maxCutPercent = Double.parseDouble(option(args, "--max-cut-percent", "2"));
        boolean verbose = List.of(args).contains("--verbose");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("The API key environment variable is not set");
            System.exit(1);
        }

        List<Example> examples = read(Path.of(args[0]), language);
        long continued = examples.stream().filter(Example::continued).count();
        long ended = examples.size() - continued;
        long withAgent = examples.stream().filter(e -> !e.agentLine().isBlank()).count();
        System.out.printf("%d examples (%s): %d end, %d continue; %d with the agent's line; model %s%n",
                examples.size(), language, ended, continued, withAgent, model);
        if (continued == 0 || ended == 0) {
            System.err.println("Both labels are needed to score anything");
            System.exit(1);
        }

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        ObjectMapper objectMapper = new ObjectMapper();
        Verdict[] verdicts = new Verdict[examples.size()];
        for (int i = 0; i < examples.size(); i++) {
            Example example = examples.get(i);
            verdicts[i] = judge(client, objectMapper, url, model, apiKey, maxTokens, reasoningEffort, logprobs, example);
            if (verbose) {
                System.out.printf(Locale.ROOT, "  %-10s p=%.2f %-8s %4dms agent='%s' caller='%s'%n",
                        verdicts[i].decision(), verdicts[i].finishedProbability(),
                        example.continued() ? "continue" : "end", verdicts[i].latencyMs(),
                        example.agentLine(), example.text());
            }
        }

        printBaseline(examples, continued, ended);
        int cut = printDecisions(examples, verdicts, continued, ended);
        if (logprobs) {
            printThresholdSweep(examples, verdicts);
        }
        printLatency(verdicts);

        double cutPercent = 100.0 * cut / continued;
        String verdict = cutPercent > maxCutPercent ? "FAIL" : "OK";
        System.out.printf(Locale.ROOT, "%n%s: the detector judged %.1f%% of unfinished callers finished (limit %.1f%%)%n",
                verdict, cutPercent, maxCutPercent);
        if (cutPercent > maxCutPercent) {
            System.exit(1);
        }
    }

    private static Verdict judge(HttpClient client, ObjectMapper objectMapper, String url, String model,
                                 String apiKey, int maxTokens, String reasoningEffort, boolean logprobs,
                                 Example example) throws IOException, InterruptedException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0.1);
        body.put("top_p", 0.1);
        body.put("max_tokens", maxTokens);
        if (reasoningEffort != null) {
            body.put("reasoning_effort", reasoningEffort);
        }
        if (logprobs) {
            body.put("logprobs", true);
            body.put("top_logprobs", 5);
        }
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", LlmTurnDetector.PROMPT);
        String caller = PUNCTUATION.matcher(example.text()).replaceAll("").strip();
        String user = (example.agentLine().isBlank() ? "" : "Agent: " + example.agentLine() + " | ")
                + "Caller: " + caller;
        messages.addObject().put("role", "user").put("content", user);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        long started = System.nanoTime();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        long latencyMs = (System.nanoTime() - started) / 1_000_000;
        if (response.statusCode() != 200) {
            System.err.printf("  request failed (%d): %s%n", response.statusCode(), response.body());
            return new Verdict(Decision.NONE, Double.NaN, latencyMs);
        }
        JsonNode choice = objectMapper.readTree(response.body()).path("choices").path(0);
        Decision decision = decision(choice.path("message").path("content").asText(""));
        return new Verdict(decision, finishedProbability(choice.path("logprobs")), latencyMs);
    }

    /** By first letter, so an answer cut short by the token limit ("unf") still reads. */
    private static Decision decision(String answer) {
        String word = answer.strip().toLowerCase(Locale.ROOT);
        if (word.startsWith("f")) {
            return Decision.FINISHED;
        }
        if (word.startsWith("u")) {
            return Decision.UNFINISHED;
        }
        if (word.startsWith("w")) {
            return Decision.WAIT;
        }
        return Decision.NONE;
    }

    /**
     * p(finished) among the three answers, from the first token's top alternatives — the
     * counterpart of LiveKit's p(end of turn). NaN when the endpoint returned no logprobs or
     * none of the alternatives starts one of the three words.
     */
    private static double finishedProbability(JsonNode logprobs) {
        JsonNode alternatives = logprobs.path("content").path(0).path("top_logprobs");
        double finished = 0;
        double total = 0;
        for (JsonNode alternative : alternatives) {
            Decision decision = decision(alternative.path("token").asText(""));
            if (decision == Decision.NONE) {
                continue;
            }
            double probability = Math.exp(alternative.path("logprob").asDouble(Double.NEGATIVE_INFINITY));
            total += probability;
            finished += decision == Decision.FINISHED ? probability : 0;
        }
        return total > 0 ? finished / total : Double.NaN;
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
        System.out.println();
        System.out.printf(Locale.ROOT, "baseline (TranscriptTurnCues): caught-continue %.1f%%, extended-end %.1f%%, "
                        + "shortened-end %.1f%%, shortened-continue %.1f%%%n",
                percent(caught, continued), percent(extended, ended),
                percent(shortened, ended), percent(cut, continued));
    }

    /**
     * Same four columns as the baseline: finished shortens, unfinished/wait extend, no answer
     * counts as unfinished (TEN's default). "shortened-continue" is the cut-off rate.
     *
     * @return how many continue rows were judged finished
     */
    private static int printDecisions(List<Example> examples, Verdict[] verdicts, long continued, long ended) {
        int caught = 0;
        int cut = 0;
        int extended = 0;
        int shortened = 0;
        int waits = 0;
        int none = 0;
        for (int i = 0; i < examples.size(); i++) {
            Decision decision = verdicts[i].decision();
            boolean finished = decision == Decision.FINISHED;
            waits += decision == Decision.WAIT ? 1 : 0;
            none += decision == Decision.NONE ? 1 : 0;
            if (examples.get(i).continued()) {
                caught += finished ? 0 : 1;
                cut += finished ? 1 : 0;
            } else {
                extended += finished ? 0 : 1;
                shortened += finished ? 1 : 0;
            }
        }
        System.out.printf(Locale.ROOT, "llm detector: caught-continue %.1f%%, extended-end %.1f%%, "
                        + "shortened-end %.1f%%, shortened-continue %.1f%%; wait %d, no answer %d%n",
                percent(caught, continued), percent(extended, ended),
                percent(shortened, ended), percent(cut, continued), waits, none);
        return cut;
    }

    /** LiveKit's knob: below the threshold the wait is extended, at or above it the turn may close. */
    private static void printThresholdSweep(List<Example> examples, Verdict[] verdicts) {
        int scored = 0;
        for (Verdict verdict : verdicts) {
            scored += Double.isNaN(verdict.finishedProbability()) ? 0 : 1;
        }
        if (scored == 0) {
            System.out.println("no logprobs in the answers — this endpoint/model does not return them");
            return;
        }
        System.out.printf("threshold sweep over %d rows with p(finished):%n", scored);
        for (double threshold : THRESHOLDS) {
            int continued = 0;
            int ended = 0;
            int cut = 0;
            int shortened = 0;
            for (int i = 0; i < examples.size(); i++) {
                double probability = verdicts[i].finishedProbability();
                if (Double.isNaN(probability)) {
                    continue;
                }
                boolean closes = probability >= threshold;
                if (examples.get(i).continued()) {
                    continued++;
                    cut += closes ? 1 : 0;
                } else {
                    ended++;
                    shortened += closes ? 1 : 0;
                }
            }
            System.out.printf(Locale.ROOT, "  p>=%.2f: shortened-end %.1f%%, shortened-continue %.1f%%%n",
                    threshold, percent(shortened, ended), percent(cut, continued));
        }
    }

    /** The detector runs while the caller is silent, so p90 is what decides whether it is in time. */
    private static void printLatency(Verdict[] verdicts) {
        long[] latencies = Arrays.stream(verdicts).mapToLong(Verdict::latencyMs).sorted().toArray();
        System.out.printf("latency: p50 %d ms, p90 %d ms, max %d ms%n",
                latencies[latencies.length / 2],
                latencies[(int) Math.min(latencies.length - 1, Math.round(latencies.length * 0.9))],
                latencies[latencies.length - 1]);
    }

    private static double percent(int part, long whole) {
        return whole == 0 ? 0 : 100.0 * part / whole;
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
                    examples.add(new Example(row.path("agent").asText(""), text,
                            "continue".equals(row.path("label").asText())));
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
