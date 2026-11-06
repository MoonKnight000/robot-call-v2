package uz.murodjon.robotcallv2.agent.turn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/**
 * End-of-turn detection the way TEN Turn Detection does it: each new hypothesis of the
 * caller's words is sent to a language model, which answers {@code finished},
 * {@code unfinished} or {@code wait}; a newer hypothesis cancels the question about the
 * older one. Punctuation is stripped first — the recognizer's commas are its guess, not
 * the caller's. From LiveKit's detector it takes the agent's last line as context
 * ({@link LlmTurnMode#PROMPT} only; TEN's model was trained on the caller's words alone).
 *
 * <p>The verdict only moves the speech gate's wait ({@code SpeechGate#setLlmTurnCue}), and
 * only while the recognizer still says the words it was given. A failed or late answer
 * gives no verdict, leaving the timer as it was — TEN would read it as "unfinished" and
 * lean on its force timer instead.
 */
@Component
@ConditionalOnProperty(prefix = "voice-agent.llm-turn", name = "enabled", havingValue = "true")
public class LlmTurnDetector {

    private static final Logger log = LoggerFactory.getLogger(LlmTurnDetector.class);

    /** TEN strips these before asking. */
    private static final Pattern PUNCTUATION = Pattern.compile("[,.!?:;…«»\"“”]");

    /** The instruction of {@link LlmTurnMode#PROMPT}; package-private for {@code LlmTurnDetectorReplayTool}. */
    static final String PROMPT = """
            You are a turn detector for a phone call in Uzbek or Russian. You get the agent's last \
            line (when known) and the caller's words so far, as a speech recognizer heard them, \
            without punctuation. Decide whether the agent should reply now.
            Answer with exactly one word and nothing else:
            finished — the caller has said what they meant; the agent should reply now.
            unfinished — the caller stopped mid-thought and will go on.
            wait — the caller asked the agent to hold on, wait or be quiet.
            Uzbek is verb-final: a sentence that has reached its finite verb ("to'layman", "bo'ladi", \
            "bilmadim") is usually over; one that stops on a case-marked noun ("pulni", "bankga"), \
            a converb ("borib", "bo'lsa"), a conjunction ("lekin", "va", "keyin") or a bare number \
            usually is not. A short answer to a closed question ("ha", "yo'q", "ikki xonali" after \
            "necha xonali?") is finished. If unsure, answer finished unless the words clearly cannot \
            stand alone.
            Examples:
            Agent: Qarzingizni qachon to'lay olasiz? | Caller: ertaga to'layman → finished
            Agent: Qarzingizni qachon to'lay olasiz? | Caller: ertaga ish haqim tushsa → unfinished
            Agent: Uyga necha kishi kiradi? | Caller: to'rt → finished
            Agent: Manzilingizni ayting | Caller: Chilonzor tumani o'n ikkinchi → unfinished
            Agent: Hozir gaplasha olasizmi? | Caller: bir daqiqa kuting → wait
            Agent: Вам удобно говорить? | Caller: да удобно → finished
            Agent: Когда сможете оплатить? | Caller: я думаю что в конце → unfinished
            Agent: Когда сможете оплатить? | Caller: подождите минутку → wait""";

    private final LlmTurnProperties llmTurnProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public LlmTurnDetector(LlmTurnProperties llmTurnProperties, ObjectMapper objectMapper) {
        this.llmTurnProperties = llmTurnProperties;
        this.objectMapper = objectMapper;
        log.info("LLM turn detector ready (mode={}, url={}, model={})", llmTurnProperties.mode(),
                llmTurnProperties.baseUrl(), modelOrDefault("<agent fast model>"));
    }

    /**
     * A judge for one call: hand it each new hypothesis with the agent's last line, and
     * {@code onVerdict} is told what the model made of those words.
     *
     * @param fastModel the agent's fast model, used in PROMPT mode when no model is configured
     * @param onVerdict told {@code (words judged, verdict)}; from an HTTP thread
     * @param onUsage   told the tokens each answered question spent, so the call is billed for them
     */
    public BiConsumer<String, String> openCall(String channelId, String fastModel,
                                               BiConsumer<String, TurnCue> onVerdict, LlmUsageSink onUsage) {
        return new CallJudge(channelId, modelOrDefault(fastModel), onVerdict, onUsage);
    }

    private String modelOrDefault(String fastModel) {
        String configured = llmTurnProperties.model();
        return configured != null && !configured.isBlank() ? configured : fastModel;
    }

    /** One call's questions: at most one in flight, the newest. */
    private final class CallJudge implements BiConsumer<String, String> {

        private final String channelId;
        private final String model;
        private final BiConsumer<String, TurnCue> onVerdict;
        private final LlmUsageSink onUsage;
        private String lastAsked = "";
        private CompletableFuture<?> pending;

        private CallJudge(String channelId, String model, BiConsumer<String, TurnCue> onVerdict,
                          LlmUsageSink onUsage) {
            this.channelId = channelId;
            this.model = model;
            this.onVerdict = onVerdict;
            this.onUsage = onUsage != null ? onUsage : LlmUsageSink.NONE;
        }

        @Override
        public synchronized void accept(String transcript, String agentLine) {
            String caller = PUNCTUATION.matcher(transcript).replaceAll("").strip();
            if (caller.isEmpty() || caller.equalsIgnoreCase(lastAsked)) {
                return;
            }
            lastAsked = caller;
            if (pending != null) {
                pending.cancel(true); // TEN's cancel_eval: the caller has said more since
            }
            long started = System.nanoTime();
            // The request's own future is the one kept: cancelling it is what aborts the exchange.
            CompletableFuture<HttpResponse<String>> request =
                    httpClient.sendAsync(request(caller, agentLine), HttpResponse.BodyHandlers.ofString());
            pending = request;
            request.whenComplete((response, failure) -> {
                if (failure != null) {
                    // A superseded request fails with "Request cancelled" without its future reading
                    // as cancelled — every new interim produced one of these warnings.
                    Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                            ? failure.getCause() : failure;
                    if (!request.isCancelled() && !(cause instanceof CancellationException)) {
                        log.warn("[{}] LLM turn request failed: {}", channelId, failure.getMessage());
                    }
                    return;
                }
                TurnCue cue = verdict(response);
                log.debug("[{}] LLM turn: {} in {} ms for '{}'", channelId, cue,
                        (System.nanoTime() - started) / 1_000_000, caller);
                if (cue != TurnCue.UNKNOWN) {
                    onVerdict.accept(transcript, cue);
                }
            });
        }

        private HttpRequest request(String caller, String agentLine) {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("temperature", 0.1);
            body.put("top_p", 0.1);
            body.put("max_tokens", Math.max(1, llmTurnProperties.maxTokens()));
            String effort = llmTurnProperties.reasoningEffort();
            if (effort != null && !effort.isBlank()) {
                body.put("reasoning_effort", effort);
            }
            ArrayNode messages = body.putArray("messages");
            if (llmTurnProperties.mode() == LlmTurnMode.PROMPT) {
                messages.addObject().put("role", "system").put("content", PROMPT);
                String line = agentLine == null || agentLine.isBlank() ? "" : "Agent: " + agentLine.strip() + " | ";
                messages.addObject().put("role", "user").put("content", line + "Caller: " + caller);
            } else {
                messages.addObject().put("role", "user").put("content", caller);
            }
            String url = llmTurnProperties.baseUrl().replaceAll("/+$", "") + "/chat/completions";
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(Math.max(100, llmTurnProperties.timeoutMs())))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
            String apiKey = llmTurnProperties.apiKey();
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            return builder.build();
        }

        private TurnCue verdict(HttpResponse<String> response) {
            if (response.statusCode() != 200) {
                log.warn("[{}] LLM turn request returned {}: {}", channelId, response.statusCode(), response.body());
                return TurnCue.UNKNOWN;
            }
            try {
                JsonNode root = objectMapper.readTree(response.body());
                // Cerebrium wraps the OpenAI body in "result", as TEN's client allows for.
                JsonNode body = root.has("choices") ? root : root.path("result");
                reportUsage(body, model, onUsage);
                return parse(body.path("choices").path(0).path("message").path("content").asText(""));
            } catch (Exception e) {
                log.warn("[{}] LLM turn answer unreadable: {}", channelId, e.getMessage());
                return TurnCue.UNKNOWN;
            }
        }
    }

    /**
     * The OpenAI-shaped {@code usage} of one answer, to {@code sink} under the model that was asked —
     * the id the price list knows, not the endpoint's spelling of it. A cancelled question (a newer
     * interim superseded it) has no body and is not counted.
     */
    static void reportUsage(JsonNode body, String model, LlmUsageSink sink) {
        JsonNode usage = body.path("usage");
        long prompt = usage.path("prompt_tokens").asLong(0);
        long completion = usage.path("completion_tokens").asLong(0);
        if (prompt <= 0 && completion <= 0) {
            return;
        }
        long cached = usage.path("prompt_tokens_details").path("cached_tokens").asLong(0);
        sink.record(model, prompt, completion, cached);
    }

    /** By first letter, as TEN reads its token, so an answer cut short ("unf") still counts. */
    static TurnCue parse(String answer) {
        String word = answer.strip().toLowerCase(Locale.ROOT);
        if (word.startsWith("f")) {
            return TurnCue.COMPLETE;
        }
        if (word.startsWith("u")) {
            return TurnCue.INCOMPLETE;
        }
        if (word.startsWith("w")) {
            return TurnCue.WAIT;
        }
        return TurnCue.UNKNOWN;
    }
}
