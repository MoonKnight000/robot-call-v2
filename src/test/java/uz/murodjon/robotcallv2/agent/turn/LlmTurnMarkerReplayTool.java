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
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Scores the LLM as an end-of-turn judge against the examples live calls labelled
 * ({@code TurnDatasetRecorder}), next to the word rules the calls run on today
 * ({@link TranscriptTurnCues}) — phase 0 of the LLM turn-completion plan.
 *
 * <p>The model is asked for one marker per example, as Pipecat's turn-completion protocol
 * does: {@code ✓} the caller has finished, {@code ○} they will go on in a moment, {@code ◐}
 * they need longer ("hozir, qarab beraman"). It sees the agent's line the caller was
 * answering when the row has one ({@code agent}); rows recorded before that field existed
 * are judged on the caller's words alone, and the two groups are printed apart.
 *
 * <p>The costly error is {@code ✓} on a {@code continue} row: in the live pipeline that
 * closes the gate early and the bot talks over an unfinished caller. A {@code ○}/{@code ◐}
 * on an {@code end} row only adds silence.
 *
 * <p>Any OpenAI-compatible chat-completions endpoint works; the key is read from an
 * environment variable, never from the command line:
 * <pre>
 *   java -cp ... uz.murodjon.robotcallv2.agent.turn.LlmTurnMarkerReplayTool &lt;jsonl-file-or-dir&gt; \
 *        [--url=https://api.openai.com/v1/chat/completions] [--model=gpt-4.1-mini] \
 *        [--api-key-env=OPENAI_API_KEY] [--language=uz-UZ] [--max-cut-percent=2] [--verbose]
 * </pre>
 * Gemini: {@code --url=https://generativelanguage.googleapis.com/v1beta/openai/chat/completions
 * --api-key-env=GEMINI_API_KEY --model=<gemini model>}.
 *
 * <p>Exits 1 when more than {@code --max-cut-percent} of the {@code continue} rows were
 * marked {@code ✓}.
 */
public final class LlmTurnMarkerReplayTool {

    private static final String SYSTEM_PROMPT = """
            You judge whether a caller on a phone call has finished their turn. The call is in \
            Uzbek or Russian. You get the agent's last line (when known) and the caller's words \
            as the speech recognizer heard them so far, with no punctuation.
            Answer with exactly one character and nothing else:
            ✓ — the caller has finished; the agent should reply now.
            ○ — the caller is mid-sentence and will go on within a second or two.
            ◐ — the caller asked for time or is thinking ("hozir", "bir daqiqa", "qarab beraman", "сейчас", "минутку").
            Uzbek is verb-final: a sentence that has reached its finite verb ("to'layman", "bo'ladi", \
            "bilmadim") is usually over; one that stops on a case-marked noun ("pulni", "bankga"), \
            a converb ("borib", "bo'lsa"), a conjunction ("lekin", "va") or a bare number usually is not. \
            A short answer to a closed question ("ha", "yo'q", "ikki xonali" after "necha xonali?") is finished.
            When unsure, answer ○ only if the words clearly cannot stand alone; otherwise ✓.""";

    private record Example(String agentLine, String text, boolean continued) {
    }

    private enum Marker { COMPLETE, SHORT, LONG, NONE }

    private LlmTurnMarkerReplayTool() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 1) {
            System.err.println("Usage: LlmTurnMarkerReplayTool <jsonl-file-or-dir> [options]");
            System.exit(1);
        }
        String url = option(args, "--url", "https://api.openai.com/v1/chat/completions");
        String model = option(args, "--model", "gpt-4.1-mini");
        String apiKey = System.getenv(option(args, "--api-key-env", "OPENAI_API_KEY"));
        String language = option(args, "--language", "uz-UZ");
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
        System.out.printf("%d examples (%s): %d end, %d continue; %d with the agent's line%n",
                examples.size(), language, ended, continued, withAgent);
        if (continued == 0 || ended == 0) {
            System.err.println("Both labels are needed to score anything");
            System.exit(1);
        }

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        ObjectMapper objectMapper = new ObjectMapper();
        Marker[] markers = new Marker[examples.size()];
        long latencyTotalMs = 0;
        for (int i = 0; i < examples.size(); i++) {
            Example example = examples.get(i);
            long started = System.nanoTime();
            markers[i] = judge(client, objectMapper, url, model, apiKey, example);
            latencyTotalMs += (System.nanoTime() - started) / 1_000_000;
            if (verbose) {
                System.out.printf("  %-8s %-8s agent='%s' caller='%s'%n", markers[i],
                        example.continued() ? "continue" : "end", example.agentLine(), example.text());
            }
        }

        printBaseline(examples, continued, ended);
        printMarkers("llm (all rows)", examples, markers, e -> true);
        if (withAgent > 0 && withAgent < examples.size()) {
            printMarkers("llm (with agent line)", examples, markers, e -> !e.agentLine().isBlank());
            printMarkers("llm (caller words only)", examples, markers, e -> e.agentLine().isBlank());
        }
        System.out.printf(Locale.ROOT, "mean request latency: %d ms%n", latencyTotalMs / examples.size());

        int cut = 0;
        for (int i = 0; i < examples.size(); i++) {
            cut += examples.get(i).continued() && markers[i] == Marker.COMPLETE ? 1 : 0;
        }
        double cutPercent = 100.0 * cut / continued;
        if (cutPercent > maxCutPercent) {
            System.out.printf(Locale.ROOT, "%nFAIL: the LLM marked %.1f%% of unfinished callers finished (limit %.1f%%)%n",
                    cutPercent, maxCutPercent);
            System.exit(1);
        }
        System.out.printf(Locale.ROOT, "%nOK: the LLM marked %.1f%% of unfinished callers finished (limit %.1f%%)%n",
                cutPercent, maxCutPercent);
    }

    private static Marker judge(HttpClient client, ObjectMapper objectMapper, String url, String model,
                                String apiKey, Example example) throws IOException, InterruptedException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0);
        body.put("max_tokens", 4);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", SYSTEM_PROMPT);
        String user = (example.agentLine().isBlank() ? "" : "Agent: " + example.agentLine() + "\n")
                + "Caller: " + example.text();
        messages.addObject().put("role", "user").put("content", user);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            System.err.printf("  request failed (%d): %s%n", response.statusCode(), response.body());
            return Marker.NONE;
        }
        String answer = objectMapper.readTree(response.body())
                .path("choices").path(0).path("message").path("content").asText("").strip();
        if (answer.startsWith("✓")) {
            return Marker.COMPLETE;
        }
        if (answer.startsWith("○")) {
            return Marker.SHORT;
        }
        if (answer.startsWith("◐")) {
            return Marker.LONG;
        }
        return Marker.NONE;
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
                100.0 * caught / continued, 100.0 * extended / ended,
                100.0 * shortened / ended, 100.0 * cut / continued);
    }

    /**
     * Same four columns as the baseline: ✓ shortens, ○/◐ extend, no marker leaves the wait alone.
     * "shortened-continue" is the cut-off rate.
     */
    private static void printMarkers(String label, List<Example> examples, Marker[] markers,
                                     Predicate<Example> include) {
        int continued = 0;
        int ended = 0;
        int caught = 0;
        int cut = 0;
        int extended = 0;
        int shortened = 0;
        int none = 0;
        for (int i = 0; i < examples.size(); i++) {
            Example example = examples.get(i);
            if (!include.test(example)) {
                continue;
            }
            Marker marker = markers[i];
            boolean incomplete = marker == Marker.SHORT || marker == Marker.LONG;
            none += marker == Marker.NONE ? 1 : 0;
            if (example.continued()) {
                continued++;
                caught += incomplete ? 1 : 0;
                cut += marker == Marker.COMPLETE ? 1 : 0;
            } else {
                ended++;
                extended += incomplete ? 1 : 0;
                shortened += marker == Marker.COMPLETE ? 1 : 0;
            }
        }
        System.out.printf(Locale.ROOT, "%s: caught-continue %.1f%%, extended-end %.1f%%, "
                        + "shortened-end %.1f%%, shortened-continue %.1f%%, no marker %d%n",
                label, percent(caught, continued), percent(extended, ended),
                percent(shortened, ended), percent(cut, continued), none);
    }

    private static double percent(int part, int whole) {
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
