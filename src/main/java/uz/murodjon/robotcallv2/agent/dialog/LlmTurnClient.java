package uz.murodjon.robotcallv2.agent.dialog;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import uz.murodjon.robotcallv2.agent.metrics.LlmMetrics;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunction;
import uz.murodjon.robotcallv2.aiagent.domain.entity.LlmFallback;
import uz.murodjon.robotcallv2.aiagent.domain.enums.LlmFallbackMode;
import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel;
import uz.murodjon.robotcallv2.aimodel.domain.entity.OwnLlmEndpoint;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.scenario.domain.entity.ToolDef;
import uz.murodjon.robotcallv2.shared.dialog.PreToolPhrases;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * The model end of a cascade turn: owns the call's {@link ChatClient}, sends the request
 * (streamed or blocking), speaks the streamed sentences as they arrive, runs the tools the
 * reply called, and accounts for the tokens.
 *
 * <p>Owns the ChatClient, and therefore whether dialog is possible at all: without an LLM
 * ChatModel bean (no {@code GEMINI_API_KEY} or {@code OPENAI_API_KEY}) the app still
 * starts and {@link #isReady()} is false.
 */
@Component
class LlmTurnClient {

    private static final Logger log = LoggerFactory.getLogger(LlmTurnClient.class);

    /** Runs the blocking model calls, so that {@link #callWithDeadline} can stop waiting on one. */
    private static final ExecutorService BLOCKING_LLM_CALLS = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * How long the paused bot waits for {@link #isTakingTheFloor}. A person who has been
     * talked over pauses about this long before deciding whether to carry on.
     */
    private static final long BARGE_IN_DECISION_MS = 1500;

    private static final Set<String> SILENT_CONTROL_TOOLS = Set.of(
            "transitionTo", "endCall", "recordWrongPerson", "recordDoNotCall", "waitForCaller", "switchLanguage", "noteCallerMood",
            "noteClientFact"
    );

    /** When this process last sent a turn to any LLM, for the {@code idle} field of the request log. */
    private final AtomicLong lastLlmRequestNanos = new AtomicLong();
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final DialogProperties dialogProperties;
    private final SystemPromptFactory promptFactory;
    private final TurnTools turnTools;
    private final SpeechOutput speech;
    private final DialogExecutors executors;
    private final LlmMetrics metrics;
    private final LlmTimeoutProperties llmTimeout;
    private final CallTraceLog callTraceLog;
    private final DialogTuningProperties dialogTuning;
    private final PromptCacheProperties promptCache;
    private final ChunkerProperties chunkerProperties;

    private volatile ChatClient chatClient;

    LlmTurnClient(ObjectProvider<ChatModel> chatModelProvider,
                  DialogProperties dialogProperties,
                  SystemPromptFactory promptFactory,
                  TurnTools turnTools,
                  SpeechOutput speech,
                  DialogExecutors executors,
                  LlmMetrics metrics,
                  LlmTimeoutProperties llmTimeout,
                  DialogTuningProperties dialogTuning,
                  CallTraceLog callTraceLog,
                  PromptCacheProperties promptCache,
                  ChunkerProperties chunkerProperties) {
        this.chatModelProvider = chatModelProvider;
        this.dialogProperties = dialogProperties;
        this.promptFactory = promptFactory;
        this.turnTools = turnTools;
        this.speech = speech;
        this.executors = executors;
        this.metrics = metrics;
        this.llmTimeout = llmTimeout;
        this.callTraceLog = callTraceLog;
        this.dialogTuning = dialogTuning;
        this.promptCache = promptCache;
        this.chunkerProperties = chunkerProperties;
    }

    @PostConstruct
    void init() {
        ChatModel model = chatModelProvider.getIfAvailable();
        if (model != null) {
            // Spring AI's own tool-calling loop stays off — {@link TurnTools#run} executes
            // the calls here instead. (Spring AI 2.0 moved that loop out of the chat model
            // and into an advisor the ChatClient registers for you, which is why this is no
            // longer the options flag it used to be.)
            //
            // That loop answers a tool call by replaying the model's own functionCall back
            // to it alongside the result, and Gemini 3 rejects the replay unless every call
            // carries the opaque thought_signature it was issued with. It also costs a
            // second round trip per tool-calling turn, and that round trip is where the
            // caller was read the stage annex out loud.
            //
            // Nothing is lost by running them ourselves: the tools move the FSM and record
            // outcomes, a data tool's result goes back as text in a follow-up round, and the
            // line for the caller travels in the tool's own `reply` argument.
            chatClient = TurnTools.chatClient(model);
            log.info("Dialog engine ready (LLM model bean: {})", model.getClass().getSimpleName());
        } else {
            log.warn("Dialog engine has no LLM ChatModel (set GEMINI_API_KEY or OPENAI_API_KEY); dialog disabled");
        }
    }

    /** The company's own LLM when the call runs on one, the platform's otherwise. */
    private ChatClient clientFor(DialogSession s) {
        ChatClient own = s.ownChatClient();
        return own != null ? own : chatClient;
    }

    /** Whether there is a model to send a turn to. */
    boolean isReady() {
        return chatClient != null;
    }

    /**
     * Whether a caller who spoke over the bot is taking the floor (an objection, a "no", a
     * question, a correction) rather than letting it carry on ("aha", a cough, agreement).
     *
     * <p>Asked of the model, not of a word list: "ha" is agreement over one line and the
     * answer to the question in another, and only the line it was said over can tell
     * which. The fast model with no tools, because the bot is paused while this runs.
     *
     * <p>Fails toward yielding — no model, no answer in {@link #BARGE_IN_DECISION_MS}, or
     * an answer it cannot read all mean the bot stops: talking over a caller who meant
     * it is the worse of the two mistakes.
     *
     * @return the model's ruling, or null when it gave none — the caller yields on null,
     *         but {@link FloorLearning} must not learn a word from a timeout
     */
    Boolean isTakingTheFloor(DialogSession s, String botLine, String callerText) {
        ChatClient client = clientFor(s);
        if (client == null) {
            return null;
        }
        String prompt = "Telefon suhbati. Bot shu gapni aytayotgan edi: \""
                + PromptSafeText.sanitize(botLine == null ? "" : botLine, 300)
                + "\". Mijoz uning ustidan dedi: \"" + PromptSafeText.sanitize(callerText, 200) + "\".\n"
                + "Mijoz botni to'xtatib o'zi gapirmoqchimi (e'tiroz, rad etish, savol, tuzatish, "
                + "botning savoliga javob), yoki shunchaki tinglayotganini bildirib botning davom "
                + "etishini kutyaptimi (ma'qullash, tasdiq tovushi, yo'tal, shovqin)?\n"
                + "Faqat bitta so'z bilan javob bering: STOP yoki CONTINUE.";
        try {
            ChatResponse response = callWithDeadline(() -> client.prompt()
                    .user(prompt)
                    .options(turnTools.buildOptions(s, List.of(), resolveFastModel(s)).mutate())
                    .call()
                    .chatResponse(), BARGE_IN_DECISION_MS);
            TokenUsage usage = new TokenUsage();
            usage.add(response);
            publishUsage(s, usage);
            String answer = extractText(response);
            String ruling = answer == null ? "" : answer.strip().toUpperCase(Locale.ROOT);
            if (ruling.startsWith("CONTINUE")) {
                return false;
            }
            return ruling.startsWith("STOP") ? true : null;
        } catch (RuntimeException e) {
            log.warn("[{}] barge-in decision failed ({}) — yielding to the caller", s.channelId(), e.getMessage());
            return null;
        }
    }

    /**
     * One turn, streamed or blocking as configured.
     *
     * @param speculated a reply already in flight for these exact words, or {@code null}
     *                   to send the request now (streaming only)
     */
    TurnResult runTurn(DialogSession s, String system, List<Message> messages,
                       List<ToolCallback> tools, Flux<ChatResponse> speculated, String model) {
        return dialogProperties.streaming()
                ? streamTurn(s, system, messages, tools, speculated, model)
                : blockingTurn(s, system, messages, tools, model);
    }

    /**
     * One more attempt at a turn whose request failed before it produced anything.
     *
     * <p>A rate limit or a 503 costs the caller the whole answer — they get the fallback
     * line, and whatever they just said goes unanswered. One retry recovers most of them
     * for a few hundred milliseconds, which the filler already covers.
     *
     * <p>Three conditions, all of them about not making it worse. The failure has to look
     * transient, or the retry is a second round trip to the same wall. Nothing may have
     * been spoken yet, or the caller would hear the first sentences twice. And the caller
     * must not have started talking in the meantime, because then the answer is stale
     * before it arrives.
     */
    TurnResult retryTurn(DialogSession s, String system, List<Message> messages,
                         List<ToolCallback> tools, String model, Exception failure) {
        // A turn that ran on the fast model is retried whatever went wrong, and always on
        // the company's own model. The override is an optimisation, and its most likely
        // failure is a model id this API key cannot reach — which 404s identically every
        // time, so retrying it against itself would only lose the caller the turn twice.
        boolean fastModelFailed = model != null;
        // The agent's sequential fallback is this retry: the main model failed, so the turn goes to the
        // fallback instead of to the same model again, whatever the failure — a model the API cannot
        // reach fails identically every time, which is what a fallback is for.
        String fallbackModel = !fastModelFailed ? sequentialFallbackModel(s) : null;
        if (!fastModelFailed && fallbackModel == null && !isTransient(failure)) {
            return TurnResult.NOTHING;
        }
        // spokenText() is null, not empty, when this turn has put nothing on the wire.
        if (s.spokenText() != null || s.isCancelled() || s.isEnded()) {
            return TurnResult.NOTHING;
        }
        try {
            Thread.sleep(dialogTuning.llmRetryBackoffMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TurnResult.NOTHING;
        }
        metrics.llmRetry();
        if (fastModelFailed) {
            log.warn("[{}] fast model '{}' failed ({}) — retrying on the configured model",
                    s.channelId(), model, failure.getMessage());
        } else if (fallbackModel != null) {
            log.warn("[{}] main model failed ({}) — retrying on the fallback model '{}'",
                    s.channelId(), failure.getMessage(), fallbackModel);
        } else {
            log.info("[{}] retrying the turn after a transient LLM failure", s.channelId());
        }
        try {
            // No speculation this time: the guess was consumed by the attempt that failed.
            // No model override either, other than the agent's fallback — see above.
            return dialogProperties.streaming()
                    ? streamTurn(s, system, messages, tools, null, fallbackModel)
                    : blockingTurn(s, system, messages, tools, fallbackModel);
        } catch (Exception e) {
            metrics.llmError();
            log.warn("[{}] the retry failed too: {}", s.channelId(), e.getMessage());
            return TurnResult.NOTHING;
        }
    }

    /**
     * Send a speculative request whose chunks are buffered until a turn adopts it.
     *
     * <p>A replaying connectable over an eager subscription is the whole mechanism: the
     * request goes out now and every chunk is buffered, so the turn that adopts it is
     * replayed all of them at once and then carries on with the live stream. Unlike
     * cache(), it hands back the connection — disposing that is what actually stops the
     * request when the guess is thrown away (cache() never cancelled upstream, so every
     * discarded guess still streamed, and was billed, to the end).
     *
     * @param upstream receives the connection once the first subscriber connects it
     */
    Flux<ChatResponse> openSpeculativeStream(DialogSession s, String system, List<Message> messages,
                                             List<ToolCallback> tools, String model,
                                             AtomicReference<Disposable> upstream) {
        return clientFor(s).prompt()
                .system(system)
                .messages(messages)
                .options(turnTools.buildOptions(s, tools, model).mutate())
                .stream()
                .chatResponse()
                .replay()
                .autoConnect(1, upstream::set);
    }

    /** A blocking request with no chat options, under the blocking-call deadline. */
    ChatResponse callWithoutOptions(DialogSession s, String system, List<Message> messages) {
        return callWithDeadline(() -> clientFor(s).prompt()
                .system(system)
                .messages(messages)
                .call()
                .chatResponse(), llmTimeout.callTimeoutMs());
    }

    /** Add a turn's tokens to the call total (for the budget) and publish the metrics. */
    void publishUsage(DialogSession s, TokenUsage usage) {
        usage.publish(s, metrics);
        s.addTokens(usage.total());
        if (usage.total() > 0) {
            callTraceLog.recordLlmUsage(s, usage.promptTokens(), usage.cachedTokens(), usage.completionTokens(),
                    usage.thinkingTokens());
        }
    }

    /** Names, descriptions and schemas in request order — any change here moves the cached prefix. */
    private static String toolsetHash(List<ToolCallback> tools) {
        StringBuilder toolset = new StringBuilder();
        for (ToolCallback tool : tools) {
            toolset.append(tool.getToolDefinition().name()).append('\u0000')
                    .append(tool.getToolDefinition().description()).append('\u0000')
                    .append(tool.getToolDefinition().inputSchema()).append('\u0001');
        }
        return Integer.toHexString(toolset.toString().hashCode());
    }

    /**
     * Sends the call's prompt once while the greeting plays, so the caller's first turn is not
     * the call's first request: by then the connection is open and the system prompt and tools
     * are in the provider's prompt cache (the first turn of a call read "cached 0").
     *
     * <p>One output token and nothing kept: the reply is not read, the history is not touched.
     * Its tokens are billed to the call like any turn's — they are the same tokens the first
     * turn would otherwise have paid for uncached, so the extra cost is only on calls that end
     * before the caller says anything.
     *
     * <p>The prefix is built here and not stored on the session: a turn builds its own, and if
     * the call's context changed in between, the warm-up only misses — the turn is unaffected.
     * Skipped when the greeting comes from the model: that request warms the cache itself.
     */
    void warmUp(DialogSession s) {
        if (!promptCache.warmup() || !isReady() || s.firstMessage() == null || s.firstMessage().isBlank()) {
            return;
        }
        // Off the starting thread: nothing here may delay or break the start of the dialog.
        executors.submit(() -> {
            try {
                String system = promptFactory.stablePrefix(s);
                List<ToolCallback> tools = turnTools.build(s);
                String mainModel = turnTools.buildOptions(s, tools, null).getModel();
                for (String model : warmupModels(mainModel, resolveFastModel(s), promptCache.warmupFastModel())) {
                    executors.submit(() -> warmUpModel(s, system, tools, model));
                }
            } catch (Exception e) {
                log.info("[{}] llm warmup skipped: {}", s.channelId(), e.getMessage());
            }
        });
    }

    /** Asks the fast model for the words a person says while they think — never the answer. */
    static final String BRIDGE_PROMPT = """
            You are on a phone call. The caller has just spoken, and your full reply will take a \
            moment. A real person usually says NOTHING in that moment and simply answers — so by \
            default write exactly: -
            Write words instead only when a person would really react out loud: the caller has just \
            given a real answer, a request or an objection that deserves a short reaction. Never \
            after a bare yes/no, a greeting reply or a short confirmation ("ha", "bor", "yo'q", \
            "xo'p", "да", "нет") — write - there. Never write bare one-word fillers like "Xo'p." or \
            "Tushunarli." as a bridge. Then write one to three words in %s. NEVER \
            repeat or paraphrase the caller's words — a person who echoes everything back sounds \
            like a robot. Do not answer, do not ask anything, do not promise to check or do \
            anything, no numbers. If the caller repeats something they already said or complains \
            that they did, apologise in those few words.
            Examples: "Ha, albatta." · "Kechirasiz." · \
            "Да, конечно." · "Извините.\"""";

    /** A bridge longer than this is an answer, not a bridge. */
    private static final int BRIDGE_MAX_WORDS = 8;

    /**
     * A short line tied to what the caller just said, for the start of a slow turn — "Uch
     * xonali, yaxshi." instead of a canned "Bir lahza." that ignores them, and an apology where
     * they had to repeat themselves. Written by the fast model with no tools, while the main
     * reply is still being written. {@code null} when it fails, has nothing natural to say, or
     * says more than a bridge may (a question, a figure, a sentence that answers): the canned
     * line covers the wait then.
     */
    String writeBridgeLine(DialogSession s) {
        String caller = s.findLastCallerText();
        if (!isReady() || caller == null || caller.isBlank() || s.isEnded()) {
            return null;
        }
        try {
            String agentLine = s.lastAgentText();
            List<String> said = s.findBridgesSaid();
            String user = (agentLine == null || agentLine.isBlank() ? "" : "Agent's last line: " + agentLine.strip() + "\n")
                    + "Caller: " + caller.strip()
                    // Left to itself the fast model writes "Xo'p." on nearly every turn.
                    + (said.isEmpty() ? "" : "\nAlready said on this call — use different words: "
                            + String.join(" · ", said));
            ChatResponse response = callWithDeadline(() -> clientFor(s).prompt()
                    .system(BRIDGE_PROMPT.formatted(SystemPromptFactory.languageName(s.language())))
                    .messages(List.of(new UserMessage(user)))
                    .options(turnTools.buildOptions(s, List.of(), resolveFastModel(s)).mutate().maxTokens(32))
                    .call()
                    .chatResponse(), llmTimeout.callTimeoutMs());
            TokenUsage usage = new TokenUsage();
            usage.add(response);
            publishUsage(s, usage);
            return acceptBridgeLine(extractText(response), caller);
        } catch (Exception e) {
            log.debug("[{}] bridge line skipped: {}", s.channelId(), e.getMessage());
            return null;
        }
    }

    /**
     * The bridge if it is one and does not parrot the caller, else {@code null}. The bridge is
     * said on nearly every turn (the model's first token is slower than the filler threshold),
     * so a recorded call answered every answer with it: "Navoiydan, tushundim." · "Uch xonali,
     * tushundim." · "Navoiy shahridan, xo'p." — and then the reply said the same words again.
     */
    static String acceptBridgeLine(String text, String caller) {
        String line = acceptBridgeLine(text);
        if (line == null || caller == null || line.toLowerCase(Locale.ROOT).startsWith("kechirasiz")
                || line.toLowerCase(Locale.ROOT).startsWith("извините")) {
            return line; // an apology may name what the caller had to say twice
        }
        Set<String> callerWords = new HashSet<>();
        for (String word : caller.toLowerCase(Locale.ROOT).split("[^\\p{L}'ʻʼ‘’`]+")) {
            if (word.length() >= 4) {
                callerWords.add(word);
            }
        }
        for (String word : line.toLowerCase(Locale.ROOT).split("[^\\p{L}'ʻʼ‘’`]+")) {
            if (word.length() >= 4 && callerWords.stream().anyMatch(c -> c.startsWith(word) || word.startsWith(c))) {
                return null;
            }
        }
        return line;
    }

    /** The fast model's bridge if it is one, else {@code null}. */
    static String acceptBridgeLine(String text) {
        if (text == null) {
            return null;
        }
        String line = text.strip().replaceAll("^[\"«“]+|[\"»”]+$", "").strip();
        if (line.isEmpty() || line.equals("-") || line.contains("?") || line.matches(".*\\d.*")
                || line.split("\\s+").length > BRIDGE_MAX_WORDS || SpeechSanitizer.isUnspeakable(line)) {
            return null;
        }
        return line;
    }

    /** Asks the fast model to check on a quiet caller in words tied to the conversation. */
    static final String STILL_THERE_PROMPT = """
            You are on a phone call, and the caller has said nothing for several seconds after \
            your last line. Write what a polite person says now to check the caller is still on \
            the line and to bring them back to the conversation: tied to your last line, in %s, \
            one or two short sentences, at most 15 words, ending with a question. No numbers, \
            sums or dates, nothing new, no promises. If nothing natural fits, write exactly: -
            Examples: "Alo, aka, eshityapsizmi? Qaysi kuni to'lay olasiz, demoqchi edim." · \
            "Aloqadamisiz? Uy necha xonali bo'lsin, deb so'rayotgan edim." · \
            "Алло, вы меня слышите? Я спрашивал, когда вам удобно оплатить.\"""";

    /** A still-there check longer than this has stopped being a check. */
    private static final int STILL_THERE_MAX_WORDS = 20;

    /**
     * The first "are you still there?" on a quiet line, in words about the conversation —
     * "Aloqadamisiz? Uy necha xonali bo'lsin, deb so'rayotgan edim." instead of the canned
     * "Alo, meni eshityapsizmi?". Written by the fast model with no tools. {@code null} when it
     * fails, has nothing natural to say, or says more than a check may (a figure, no question):
     * the canned line and the repeated question cover it then.
     */
    String writeStillThereLine(DialogSession s) {
        String agentLine = s.lastAgentText();
        if (!isReady() || agentLine == null || agentLine.isBlank() || s.isEnded()) {
            return null;
        }
        try {
            String caller = s.findLastCallerText();
            String user = (caller == null || caller.isBlank() ? "" : "Caller's last words: " + caller.strip() + "\n")
                    + "Your last line: " + agentLine.strip();
            ChatResponse response = callWithDeadline(() -> clientFor(s).prompt()
                    .system(STILL_THERE_PROMPT.formatted(SystemPromptFactory.languageName(s.language())))
                    .messages(List.of(new UserMessage(user)))
                    .options(turnTools.buildOptions(s, List.of(), resolveFastModel(s)).mutate().maxTokens(64))
                    .call()
                    .chatResponse(), llmTimeout.callTimeoutMs());
            TokenUsage usage = new TokenUsage();
            usage.add(response);
            publishUsage(s, usage);
            return acceptStillThereLine(extractText(response));
        } catch (Exception e) {
            log.debug("[{}] still-there line skipped: {}", s.channelId(), e.getMessage());
            return null;
        }
    }

    /** The fast model's still-there check if it is one, else {@code null}. */
    static String acceptStillThereLine(String text) {
        if (text == null) {
            return null;
        }
        String line = text.strip().replaceAll("^[\"«“]+|[\"»”]+$", "").strip();
        if (line.isEmpty() || line.equals("-") || !line.contains("?") || line.matches(".*\\d.*")
                || line.split("\\s+").length > STILL_THERE_MAX_WORDS || SpeechSanitizer.isUnspeakable(line)) {
            return null;
        }
        return line;
    }

    /**
     * The model overrides a warm-up is sent on: {@code null} — the call's own model — and the fast
     * one when asked for and different, since the provider's cache is per model.
     */
    static List<String> warmupModels(String mainModel, String fastModel, boolean includeFastModel) {
        if (!includeFastModel || fastModel == null || fastModel.isBlank() || fastModel.equals(mainModel)) {
            return Collections.singletonList(null);
        }
        return Arrays.asList(null, fastModel);
    }

    private void warmUpModel(DialogSession s, String system, List<ToolCallback> tools, String model) {
        if (s.isEnded()) {
            return;
        }
        long started = System.nanoTime();
        lastLlmRequestNanos.set(started);
        try {
            ChatResponse response = callWithDeadline(() -> clientFor(s).prompt()
                    .system(system)
                    .messages(List.of(new UserMessage(".")))
                    .options(turnTools.buildOptions(s, tools, model).mutate().maxTokens(1))
                    .call()
                    .chatResponse(), llmTimeout.callTimeoutMs());
            TokenUsage usage = new TokenUsage();
            usage.add(response);
            publishUsage(s, usage);
            long tookMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
            if (model == null) {
                // One token back: the first estimate of this call's wait (SpeechOutput#scheduleFiller).
                s.latency().warmupMeasured(tookMs);
            }
            log.info("[{}] llm warmup: model={}, prefix={}, toolset={}, done in {} ms", s.channelId(),
                    model != null ? model : "main", Integer.toHexString(system.hashCode()), toolsetHash(tools),
                    tookMs);
        } catch (Exception e) {
            // A warm-up is a head start, never a requirement: the first turn simply starts cold.
            log.info("[{}] llm warmup failed (model={}): {}", s.channelId(), model != null ? model : "main",
                    e.getMessage());
        }
    }

    /** The stable, per-call half of the prompt — built once and then reused verbatim. */
    /**
     * A flow stage's job asked of the model off the call ({@code TurnRunner}): one request whose tool calls are run
     * and whose text is dropped — the caller is already hearing the next stage. The normal model, not the fast
     * one: the arguments (a date, a sum) are read out of the conversation. Never throws; a job that fails is logged,
     * and its tool's own execution log has the rest.
     *
     * @param tools the job's own tool and nothing else, so nothing here can move the call
     */
    void runStageJob(DialogSession s, String system, List<Message> messages, List<ToolCallback> tools) {
        try {
            ChatResponse response = callWithDeadline(() -> clientFor(s).prompt()
                    .system(system)
                    .messages(messages)
                    .options(turnTools.buildOptions(s, tools, null).mutate())
                    .call()
                    .chatResponse(), llmTimeout.callTimeoutMs());
            TokenUsage usage = new TokenUsage();
            usage.add(response);
            publishUsage(s, usage);
            List<AssistantMessage.ToolCall> calls = turnTools.extractCalls(response);
            if (calls.isEmpty()) {
                log.warn("[{}] background stage job called no tool", s.channelId());
                return;
            }
            for (AssistantMessage.ToolCall call : calls) {
                ToolCallback callback = tools.stream()
                        .filter(tool -> tool.getToolDefinition().name().equals(call.name()))
                        .findFirst()
                        .orElse(null);
                if (callback == null) {
                    continue;
                }
                String result = callback.call(call.arguments());
                log.info("[{}] background stage job {}({}) -> {}", s.channelId(), call.name(), call.arguments(), result);
            }
        } catch (Exception e) {
            log.warn("[{}] background stage job failed: {}", s.channelId(), e.getMessage());
        }
    }

    String resolveSystemPrefix(DialogSession s) {
        String prefix = s.systemPrefix();
        if (prefix == null) {
            prefix = promptFactory.stablePrefix(s);
            s.setSystemPrefix(prefix);
        }
        return prefix;
    }

    /**
     * The model this turn runs on: the small one when the caller said almost nothing, the
     * company's configured one otherwise.
     *
     * <p>Most turns of a collections call are "ha", "to'ladim", "kim bo'lasiz" — an
     * acknowledgement or a one-line question, answered the same way by any model, and paid
     * for in time-to-first-token on every single one of them. The turns that actually need
     * the larger model are the ones where the caller argues, and those are not short.
     *
     * <p>Word count rather than intent on purpose: an intent classifier is another model
     * call in front of the model call it was supposed to save. The single exception is a
     * lexical one, not a classifier — a short answer that names a number goes to the full
     * model regardless of length (see below). Blank {@code fast-model} disables the whole
     * thing, and the greeting never uses it.
     */
    String selectModel(DialogSession s, String clientText, boolean fromClient) {
        String fast = resolveFastModel(s);
        if (fast == null || fast.isBlank() || !fromClient || clientText == null) {
            return null;
        }
        String trimmed = clientText.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        // A number is the one short answer that is not cheap. On a recorded call the
        // caller answered "yetti" — one word, so the fast model took it — and the reply
        // that came back was "Oyning 7-sanasini aytyapsizftmi?": a mangled word the
        // synthesizer read out as it was written, on the turn that decides which day the
        // payment is promised for and may call recordPaymentPromise. Short and numeric is
        // exactly the shape this optimisation must not cover, so it is the one exception
        // to word count. Only numbers: a relative date word ("ertaga") carries the same
        // stakes and is still on the fast path — add it here if it starts costing turns.
        if (UzbekNumberParser.containsNumber(trimmed)) {
            return null;
        }
        return trimmed.split("\\s+").length <= dialogProperties.fastModelMaxWords() ? fast : null;
    }

    /**
     * The cheap model for this call: the agent's own if it named one, otherwise the
     * installation's {@code dialog.fast-model}.
     *
     * <p>An agent that wants no downgrade sets its fast model to the same id as its
     * {@code llmModel} — there is no separate off switch, because "the cheap model is the
     * same as the careful one" already says it and one field cannot then disagree with
     * another.
     *
     * <p>On the company's own LLM it is that endpoint's fast model or none: neither the agent's
     * nor the installation's id names a model the company's endpoint serves.
     */
    String resolveFastModel(DialogSession s) {
        OwnLlmEndpoint ownLlm = s.aiModel() != null ? s.aiModel().ownLlm() : null;
        if (ownLlm != null) {
            return ownLlm.fastModel();
        }
        String agentModel = s.fastModel();
        return agentModel != null && !agentModel.isBlank() && !AiModel.isRealtimeModel(agentModel)
                ? agentModel : dialogProperties.fastModel();
    }

    /**
     * The agent's fallback LLM, or null — also on the company's own LLM, whose endpoint serves its own
     * model ids and not the catalog's.
     */
    private static LlmFallback fallbackOf(DialogSession s) {
        boolean ownLlm = s.aiModel() != null && s.aiModel().ownLlm() != null;
        return ownLlm || s.agent() == null ? null : s.agent().llmFallback();
    }

    /** The model a failed turn is retried on, or null when the agent has no sequential fallback. */
    private static String sequentialFallbackModel(DialogSession s) {
        LlmFallback fallback = fallbackOf(s);
        return fallback != null && fallback.mode() == LlmFallbackMode.SEQUENTIAL ? fallback.model() : null;
    }

    /** The assistant text carried by one response (or chunk), or {@code null}. */
    static String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        // The one place model text enters, streaming and blocking alike, so it is where a
        // character no telephone voice can say is dropped before anything reads the line.
        return SpeechSanitizer.stripUnspeakableCharacters(response.getResult().getOutput().getText());
    }

    /**
     * Whether a failure is worth a second attempt: the provider was busy, unavailable or
     * slow, rather than refusing the request. The exception type answers when it can — a
     * timeout, a dropped connection, a 429 or 5xx from a Spring HTTP client — and the
     * message text covers the vendors whose SDKs flatten the status into a string. A
     * wrong guess here costs one extra request, so the list stays on the clear cases.
     */
    private static boolean isTransient(Throwable failure) {
        for (Throwable t = failure; t != null && t != t.getCause(); t = t.getCause()) {
            if (t instanceof TimeoutException || t instanceof IOException
                    || t instanceof HttpServerErrorException
                    || t instanceof HttpClientErrorException.TooManyRequests) {
                return true;
            }
            String message = t.getMessage();
            if (message == null) {
                continue;
            }
            String lower = message.toLowerCase();
            if (lower.contains("429") || lower.contains("rate limit") || lower.contains("quota")
                    || lower.contains("500") || lower.contains("502") || lower.contains("503")
                    || lower.contains("504") || lower.contains("overloaded") || lower.contains("unavailable")
                    || lower.contains("timeout") || lower.contains("timed out")
                    || lower.contains("connection reset") || lower.contains("connection closed")
                    || lower.contains("resource_exhausted") || lower.contains("deadline exceeded")) {
                return true;
            }
        }
        return false;
    }

    /**
     * One turn, streamed: sentences are synthesized and queued as the model produces
     * them, so the caller hears the opening words while the rest is still generating.
     * This is where the turnaround budget is won — the alternative serializes the full
     * LLM latency and the full synthesis latency before any audio exists (§7.2, §1.3).
     *
     * @param speculated a reply already in flight for these exact words, or {@code null}
     *                   to send the request now. Adopting one changes nothing below it:
     *                   the stream replays what it generated during the endpointing wait
     *                   and then continues live, so sentences are spoken, tools collected
     *                   and tokens counted exactly as on any other turn
     * @return the reply text plus whether any of it actually reached the caller
     */
    private TurnResult streamTurn(DialogSession s, String system, List<Message> messages,
                                  List<ToolCallback> tools, Flux<ChatResponse> speculated, String model) {
        TokenUsage turnUsage = new TokenUsage();
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        // Read before the tools run: a transitionTo among them resets the step.
        boolean stageActionTurn = s.stageActionStep() > 0;
        // Read before the tools run too: a transitionTo among them changes the stage.
        boolean muted = isMutedStageJob(s, stageActionTurn);
        // What the request carries, so a slow or uncached turn can be traced to its size —
        // and to its model: a provider's prompt cache is per model, so a turn that switched
        // (fast model, a stage's own model) starts cold whatever the prefix.
        // The two hashes say which half broke a cache miss: Gemini matches a request by its
        // leading bytes, so a turn whose prefix and toolset hashes equal the previous turn's
        // and still reads "cached 0" was the provider's choice, not ours. `idle` is the gap
        // since this process last called any LLM — long gaps are where a pooled connection
        // has been closed and the request pays a fresh TLS handshake.
        long now = System.nanoTime();
        long previous = lastLlmRequestNanos.getAndSet(now);
        log.info("[{}] llm request: model={}, system={} chars, messages={}, tools={} ({} chars), "
                        + "prefix={}, toolset={}, idle={}", s.channelId(),
                speculated != null ? "speculated" : turnTools.buildOptions(s, tools, model).getModel(),
                system.length(), messages.size(), tools.size(), tools.stream()
                        .mapToInt(tool -> Objects.toString(tool.getToolDefinition().description(), "").length()
                                + Objects.toString(tool.getToolDefinition().inputSchema(), "").length())
                        .sum(),
                Integer.toHexString(system.hashCode()), toolsetHash(tools),
                previous == 0 ? "first" : Duration.ofNanos(now - previous).toMillis() + "ms");

        StreamedReply reply = consume(s, speculated != null ? speculated
                : withBackupModel(s, system, messages, tools, model), turnUsage, toolCalls, muted);
        publishUsage(s, turnUsage);
        TypedToolCalls.addTypedTransition(s.channelId(), reply.text(), toolCalls);
        if (muted) {
            // Tools still run — the job and the transition are what this turn is for.
            String toolNote = s.isLineGone() ? null : turnTools.run(s, tools, toolCalls);
            logMutedStageJob(s, reply.text());
            return new TurnResult("", false, false, toolNote);
        }
        maybeSpeakPreToolSpeech(s, toolCalls, reply.spoken());
        // No tool runs for a caller who has hung up — a webhook or an SMS "for" a call
        // that no longer exists.
        String toolNote = s.isLineGone() ? null : turnTools.run(s, tools, toolCalls);

        if (wantsResultRound(s, toolCalls, toolNote)) {
            TurnResult answered = streamResultRounds(s, system, messages, tools, reply, toolCalls, toolNote);
            if (answered != null) {
                return answered;
            }
        }
        if (!reply.text().isEmpty() && !SpeechSanitizer.isUnspeakable(reply.text())) {
            return new TurnResult(reply.text(), reply.spoken(), reply.blocked(), toolNote);
        }
        if (reply.spoken()) {
            // Part of the reply is already on the line; only a chunk with a tool call the model
            // typed out was refused. Asking again put a second, unrelated line on top of it
            // (call 12: "Aka, adashibsiz, men Rustam emasman"). The sentences the caller heard are
            // the reply: dropped with the rest, the greeting of call 13 never reached the history
            // or the transcript, and the model greeted again.
            return new TurnResult(reply.spokenText(), true, reply.blocked(), toolNote);
        }
        if (s.isWaitingForCaller()) {
            // The model chose silence: the caller is mid-thought. Asking it again for a
            // line would make it say exactly what it decided not to.
            return new TurnResult("", false, false, null);
        }
        // Tool-only turn: the model called e.g. transitionTo and produced no text of its
        // own, which is how this provider answers a tool-calling turn. The line it wrote
        // into the tool's `reply` argument is the answer — speak that (DialogTools).
        String carried = s.toolReplies();
        if (carried != null && !SpeechSanitizer.isUnspeakable(carried)) {
            SpeechOutcome outcome = speech.speak(s, carried);
            return new TurnResult(carried, outcome == SpeechOutcome.SPOKEN,
                    outcome == SpeechOutcome.BLOCKED, toolNote);
        }
        if (!toolCalls.isEmpty() && isSilentStageStep(s, stageActionTurn)
                || isCallersTurnToAnswer(s, stageActionTurn)) {
            return new TurnResult("", false, false, toolNote);
        }
        // Nothing spoken and nothing carried — ask once more for the line alone. A whole
        // extra round trip inside the turnaround budget (§1.3), so it is the last resort,
        // and it is asked of the fast model with no tools: all that is wanted back is one
        // sentence the model has already decided on, and the caller is sitting in silence
        // for every millisecond of it. Measured at 996ms on the default model.
        metrics.spokenLineRetry();
        log.warn("[{}] empty LLM reply in {} — asking again for the spoken line",
                s.channelId(), s.state());
        TokenUsage retryUsage = new TokenUsage();
        StreamedReply retry = consume(s, clientFor(s).prompt()
                .system(system)
                .messages(spokenLineRetry(messages, toolNote))
                .options(turnTools.buildOptions(s, List.of(), resolveFastModel(s)).mutate())
                .stream()
                .chatResponse(), retryUsage, new ArrayList<>());
        publishUsage(s, retryUsage);
        // toolNote dropped: the retry has already shown it to the model.
        return new TurnResult(retry.text(), retry.spoken(), retry.blocked(), null);
    }

    /**
     * The turn's request, raced against the fast model when the configured one is slow to
     * start ({@link LlmTimeoutProperties#backupAfterMs}). The backup is only sent once the
     * wait has passed, so a turn that starts in time costs nothing extra; the stream that
     * signals first is kept and the other one cancelled.
     */
    private Flux<ChatResponse> withBackupModel(DialogSession s, String system, List<Message> messages,
                                               List<ToolCallback> tools, String model) {
        Flux<ChatResponse> primary = clientFor(s).prompt()
                .system(system)
                .messages(messages)
                .options(turnTools.buildOptions(s, tools, model).mutate())
                .stream()
                .chatResponse();
        // The agent's concurrent fallback is the model raced; without one it is the call's fast model.
        LlmFallback fallback = fallbackOf(s);
        String backup = fallback != null && fallback.mode() == LlmFallbackMode.CONCURRENT
                ? fallback.model() : resolveFastModel(s);
        String primaryModel = turnTools.buildOptions(s, tools, model).getModel();
        if (llmTimeout.backupAfterMs() <= 0 || model != null || backup == null || backup.isBlank()
                || backup.equalsIgnoreCase(primaryModel)) {
            return primary;
        }
        Flux<ChatResponse> backupCall = clientFor(s).prompt()
                .system(system)
                .messages(messages)
                .options(turnTools.buildOptions(s, tools, backup).mutate())
                .stream()
                .chatResponse();
        // Logged when the wait runs out, not on the stream's subscribe: the Gemini client only
        // signals that once its blocking request returns — on a lost race, when it is
        // cancelled — so the line used to land with the primary's answer, ~2 s late.
        // The backup's first chunk only gets through when it won: the loser is cancelled first.
        AtomicBoolean backupAnswered = new AtomicBoolean();
        Flux<ChatResponse> backupStream = Mono.delay(Duration.ofMillis(llmTimeout.backupAfterMs()))
                .doOnNext(tick -> log.info("[{}] no first token in {} ms — racing backup model '{}'",
                        s.channelId(), llmTimeout.backupAfterMs(), backup))
                .thenMany(backupCall.subscribeOn(Schedulers.boundedElastic()))
                .doOnNext(response -> {
                    if (backupAnswered.compareAndSet(false, true)) {
                        log.info("[{}] backup model '{}' answered first", s.channelId(), backup);
                    }
                });
        // The Gemini client sends the request inside subscribe() and blocks there until the
        // response starts, so firstWithSignal — which subscribes its sources one by one — sat
        // in the primary for the whole stall and never reached the backup: no recorded call
        // ever logged the race, and a 5 s stall cost the caller 12.7 s of silence. Each
        // request subscribes on its own thread so the backup starts on time.
        return Flux.firstWithSignal(primary.subscribeOn(Schedulers.boundedElastic()), backupStream);
    }

    /**
     * Answer with a data tool's result in the same turn: the result goes back to the model as
     * text and its reply is spoken like the main one. Tools are offered again for chaining
     * (search a contact, then create a lead) until the last allowed round, which has none.
     * The normal model is used, not the fast one — the answer is what the caller hears.
     *
     * @return the turn (the first text plus the follow-up text), or {@code null} when nothing
     *         speakable came out of it and the caller falls back to the ordinary empty-reply path
     */
    private TurnResult streamResultRounds(DialogSession s, String system, List<Message> messages,
                                          List<ToolCallback> tools, StreamedReply first,
                                          List<AssistantMessage.ToolCall> calls, String toolNote) {
        String text = SpeechSanitizer.isUnspeakable(first.text()) ? "" : first.text();
        boolean spoken = first.spoken();
        boolean blocked = first.blocked();
        String stepText = text;
        List<Message> thread = messages;
        int maxRounds = dialogProperties.toolResultRounds();
        for (int round = 1; round <= maxRounds && wantsResultRound(s, calls, toolNote); round++) {
            log.info("[{}] tool result round {}: {}", s.channelId(), round, toolNames(calls));
            thread = withToolResult(thread, stepText, toolNote);
            List<ToolCallback> roundTools = round < maxRounds ? tools : List.of();
            TokenUsage usage = new TokenUsage();
            calls = new ArrayList<>();
            StreamedReply follow = consume(s, clientFor(s).prompt()
                    .system(system)
                    .messages(thread)
                    .options(turnTools.buildOptions(s, roundTools, null).mutate())
                    .stream()
                    .chatResponse(), usage, calls);
            publishUsage(s, usage);
            maybeSpeakPreToolSpeech(s, calls, follow.spoken());
            toolNote = s.isLineGone() ? null : turnTools.run(s, roundTools, calls);
            stepText = SpeechSanitizer.isUnspeakable(follow.text()) ? "" : follow.text();
            text = joinText(text, stepText);
            spoken |= follow.spoken();
            blocked |= follow.blocked();
        }
        // toolNote is what the last step's tools said that no model round has seen (usually null).
        return text.isEmpty() ? null : new TurnResult(text, spoken, blocked, toolNote);
    }

    /**
     * Drain one streamed reply: accumulate its text, collect the tool calls it carries,
     * and speak each sentence as soon as it is complete.
     *
     * <p>The stream is drained to completion even after a barge-in — abandoning the
     * iterator mid-way leaves the subscription open. Cancellation stops the synthesis,
     * which is the part that costs money and airtime.
     */
    private StreamedReply consume(DialogSession s, Flux<ChatResponse> responses,
                                  TokenUsage usage, List<AssistantMessage.ToolCall> toolCalls) {
        return consume(s, responses, usage, toolCalls, false);
    }

    /** @param muted read the text and collect the tool calls, but speak none of it ({@link #isMutedStageJob}) */
    private StreamedReply consume(DialogSession s, Flux<ChatResponse> responses,
                                  TokenUsage usage, List<AssistantMessage.ToolCall> toolCalls, boolean muted) {
        StringBuilder full = new StringBuilder();
        StringBuilder pending = new StringBuilder();
        SentenceQueue sentences = new SentenceQueue(s);

        // A stall between two chunks — the first one included — surfaces as a
        // TimeoutException here, which isTransient turns into the retry path.
        Flux<ChatResponse> bounded = (llmTimeout.tokenGapMs() > 0
                ? responses.timeout(Duration.ofMillis(llmTimeout.tokenGapMs()))
                : responses)
                // A hung-up call cancels the stream — and the tokens it would still bill —
                // instead of reading it to the end. takeWhile cancels upstream; leaving the
                // loop below would not. A barge-in still drains it (see above).
                .takeWhile(response -> !s.isLineGone());
        for (ChatResponse response : bounded.toIterable()) {
            s.latency().llmFirstToken();
            usage.add(response);
            // Collected, not run yet: a tool may end the call, and the sentences already
            // in flight should still be spoken before that takes effect.
            for (AssistantMessage.ToolCall call : turnTools.extractCalls(response)) {
                if (toolCalls.stream().noneMatch(existing ->
                        Objects.equals(existing.id(), call.id()) ||
                        (existing.name().equals(call.name()) && Objects.equals(existing.arguments(), call.arguments())))) {
                    toolCalls.add(call);
                }
            }
            String chunk = extractText(response);
            if (chunk == null || chunk.isEmpty()) {
                continue;
            }
            full.append(chunk);
            if (muted) {
                continue;
            }
            pending.append(chunk);
            if (s.isCancelled()) {
                // Still accumulating, just no longer speaking: what is left in `pending`
                // when the stream ends is exactly the part the caller did not hear, and
                // that is what a false interruption resumes from.
                continue;
            }
            for (String sentence = SentenceSplitter.takeSentence(pending, sentences.isOpening(), chunkerProperties);
                 sentence != null;
                 sentence = SentenceSplitter.takeSentence(pending, sentences.isOpening(), chunkerProperties)) {
                sentences.add(sentence);
            }
        }
        if (muted) {
            // nothing was queued for the caller
        } else if (s.isCancelled()) {
            s.setUnspokenText(pending.toString());
        } else if (!pending.isEmpty()) {
            sentences.add(pending.toString().trim());
        }
        sentences.finish();
        return new StreamedReply(full.toString().trim(), sentences.spoken(), sentences.blocked(), sentences.spokenText());
    }

    /**
     * One reply's sentences, queued for the caller in order while the next one is already
     * being synthesized.
     *
     * <p>The first sentence is spoken on the turn's own thread: it is the one on the §1.3
     * turnaround clock and goes out chunk by chunk as the synthesizer produces it
     * ({@link SpeechOutput#speak}). Every sentence after it is handed to a worker the
     * moment the model finishes writing it, and queued behind its predecessor when both
     * are ready — so the second sentence's round trip runs while the first is playing
     * instead of after it, which is where the 400-900 ms gaps between a reply's sentences
     * came from.
     */
    private final class SentenceQueue {

        private final DialogSession s;
        /** Deliveries so far, in order — each one waits for the previous. */
        private CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        private final AtomicBoolean spoken = new AtomicBoolean();
        private final AtomicBoolean blocked = new AtomicBoolean();
        /** Spoken one after another (the chain is sequential), so the order is the order heard. */
        private final List<String> spokenSentences = Collections.synchronizedList(new ArrayList<>());
        /** Whether the streamed first sentence is still to come. */
        private boolean opening = true;
        /** The sentence added just before, as written — what a synthesizer may continue from. */
        private String previousSentence;

        private SentenceQueue(DialogSession s) {
            this.s = s;
        }

        boolean isOpening() {
            return opening;
        }

        void add(String sentence) {
            String previous = previousSentence;
            previousSentence = sentence;
            if (opening) {
                SpeechOutcome outcome = speech.speak(s, sentence);
                // A refused opening line leaves the next one on the clock, and on the
                // streamed path.
                opening = outcome != SpeechOutcome.SPOKEN;
                note(outcome, sentence);
                return;
            }
            CompletableFuture<PreparedLine> prepared = executors.supply(() -> speech.prepare(s, sentence, previous));
            chain = chain.thenCombine(prepared, (done, line) -> {
                note(speech.deliver(s, line), sentence);
                return null;
            });
        }

        /**
         * Wait for every queued sentence to reach the endpoint (or be refused) — but not
         * for ever: a synthesizer that hangs on one sentence must not hold the turn.
         */
        void finish() {
            try {
                if (llmTimeout.queueDrainMs() > 0) {
                    chain.get(llmTimeout.queueDrainMs(), TimeUnit.MILLISECONDS);
                } else {
                    chain.join();
                }
            } catch (TimeoutException e) {
                log.warn("[{}] queued sentences did not finish within {} ms — moving on",
                        s.channelId(), llmTimeout.queueDrainMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (CompletionException | ExecutionException e) {
                log.warn("[{}] a queued sentence was lost: {}", s.channelId(), e.getCause().toString());
            }
        }

        boolean spoken() {
            return spoken.get();
        }

        boolean blocked() {
            return blocked.get();
        }

        /** The sentences that reached the caller, in order — what the reply was if part of it was refused. */
        String spokenText() {
            return String.join(" ", spokenSentences);
        }

        private void note(SpeechOutcome outcome, String sentence) {
            if (outcome == SpeechOutcome.SPOKEN) {
                spoken.set(true);
                spokenSentences.add(sentence);
            } else if (outcome == SpeechOutcome.BLOCKED) {
                blocked.set(true);
            }
        }
    }

    /**
     * {@code call} with a deadline on it (turns use
     * {@code voice-agent.dialog.llm-timeout.call-timeout-ms}). A blocking model call has
     * no deadline of its own. A timeout surfaces with a {@link TimeoutException} cause,
     * which {@link #isTransient} treats like the streaming path's token-gap timeout. The
     * abandoned request may still finish on its own thread; the turn no longer waits for it.
     */
    private ChatResponse callWithDeadline(Supplier<ChatResponse> call, long timeoutMs) {
        if (timeoutMs <= 0) {
            return call.get();
        }
        CompletableFuture<ChatResponse> pending = CompletableFuture.supplyAsync(call, BLOCKING_LLM_CALLS);
        try {
            return pending.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw new CompletionException(new TimeoutException("LLM call took longer than " + timeoutMs + " ms"));
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new CompletionException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pending.cancel(true);
            throw new CompletionException(e);
        }
    }

    /** One turn in a single blocking call — the pre-streaming path, kept as a fallback. */
    private TurnResult blockingTurn(DialogSession s, String system, List<Message> messages,
                                    List<ToolCallback> tools, String model) {
        boolean stageActionTurn = s.stageActionStep() > 0; // see streamTurn
        boolean muted = isMutedStageJob(s, stageActionTurn);
        ChatResponse response = callWithDeadline(() -> clientFor(s).prompt()
                .system(system)
                .messages(messages)
                .options(turnTools.buildOptions(s, tools, model).mutate())
                .call()
                .chatResponse(), llmTimeout.callTimeoutMs());
        TokenUsage turnUsage = new TokenUsage();
        turnUsage.add(response);
        publishUsage(s, turnUsage);
        List<AssistantMessage.ToolCall> calls = new ArrayList<>(turnTools.extractCalls(response));
        TypedToolCalls.addTypedTransition(s.channelId(), extractText(response), calls);
        maybeSpeakPreToolSpeech(s, calls, false);
        String toolNote = s.isLineGone() ? null : turnTools.run(s, tools, calls);
        if (muted) {
            logMutedStageJob(s, extractText(response));
            return new TurnResult("", false, false, toolNote);
        }

        if (wantsResultRound(s, calls, toolNote)) {
            TurnResult answered = blockingResultRounds(s, system, messages, tools, extractText(response), calls, toolNote);
            if (answered != null) {
                return answered;
            }
        }
        String reply = extractText(response);
        if (reply == null || reply.isBlank() || SpeechSanitizer.isUnspeakable(reply)) {
            reply = s.toolReplies(); // the line the tools carried (see DialogTools)
        }
        if (reply != null && SpeechSanitizer.isUnspeakable(reply)) {
            reply = null;
        }
        if ((reply == null || reply.isBlank()) && s.isWaitingForCaller()) {
            return new TurnResult("", false, false, null); // the model chose to keep listening
        }
        if ((reply == null || reply.isBlank())
                && (!calls.isEmpty() && isSilentStageStep(s, stageActionTurn) || isCallersTurnToAnswer(s, stageActionTurn))) {
            return new TurnResult("", false, false, toolNote);
        }
        if (reply == null || reply.isBlank()) {
            metrics.spokenLineRetry();
            log.warn("[{}] empty LLM reply in {} — asking again for the spoken line",
                    s.channelId(), s.state());
            reply = retryForSpokenLine(s, system, messages, toolNote);
            toolNote = null; // the retry has already shown it to the model
        }
        SpeechOutcome outcome = speech.speak(s, reply);
        if (outcome == SpeechOutcome.SKIPPED && s.isCancelled()) {
            // Blocking mode synthesizes the reply whole, so a barge-in costs all of it —
            // which also makes all of it what a false interruption has to resume.
            s.setUnspokenText(reply);
        }
        return new TurnResult(reply, outcome == SpeechOutcome.SPOKEN,
                outcome == SpeechOutcome.BLOCKED, toolNote);
    }

    /**
     * Blocking twin of {@link #streamResultRounds}: each step's text is spoken whole as soon
     * as the step returns.
     *
     * @param firstText what the model wrote alongside the tool calls (not spoken yet), or null
     * @return the turn, or {@code null} when nothing speakable came out of it
     */
    private TurnResult blockingResultRounds(DialogSession s, String system, List<Message> messages,
                                            List<ToolCallback> tools, String firstText,
                                            List<AssistantMessage.ToolCall> calls, String toolNote) {
        String text = "";
        boolean spoken = false;
        boolean blocked = false;
        String stepText = firstText == null || SpeechSanitizer.isUnspeakable(firstText) ? "" : firstText.trim();
        if (!stepText.isEmpty()) {
            SpeechOutcome outcome = speakBlocking(s, stepText);
            spoken = outcome == SpeechOutcome.SPOKEN;
            blocked = outcome == SpeechOutcome.BLOCKED;
            text = stepText;
        }
        List<Message> thread = messages;
        int maxRounds = dialogProperties.toolResultRounds();
        for (int round = 1; round <= maxRounds && wantsResultRound(s, calls, toolNote); round++) {
            log.info("[{}] tool result round {}: {}", s.channelId(), round, toolNames(calls));
            thread = withToolResult(thread, stepText, toolNote);
            List<Message> roundMessages = thread;
            List<ToolCallback> roundTools = round < maxRounds ? tools : List.of();
            ChatResponse response = callWithDeadline(() -> clientFor(s).prompt()
                    .system(system)
                    .messages(roundMessages)
                    .options(turnTools.buildOptions(s, roundTools, null).mutate())
                    .call()
                    .chatResponse(), llmTimeout.callTimeoutMs());
            TokenUsage usage = new TokenUsage();
            usage.add(response);
            publishUsage(s, usage);
            calls = turnTools.extractCalls(response);
            maybeSpeakPreToolSpeech(s, calls, false);
            toolNote = s.isLineGone() ? null : turnTools.run(s, roundTools, calls);
            String followText = extractText(response);
            stepText = followText == null || SpeechSanitizer.isUnspeakable(followText) ? "" : followText.trim();
            if (!stepText.isEmpty()) {
                SpeechOutcome outcome = speakBlocking(s, stepText);
                spoken |= outcome == SpeechOutcome.SPOKEN;
                blocked |= outcome == SpeechOutcome.BLOCKED;
                text = joinText(text, stepText);
            }
        }
        // toolNote is what the last step's tools said that no model round has seen (usually null).
        return text.isEmpty() ? null : new TurnResult(text, spoken, blocked, toolNote);
    }

    private SpeechOutcome speakBlocking(DialogSession s, String text) {
        SpeechOutcome outcome = speech.speak(s, text);
        if (outcome == SpeechOutcome.SKIPPED && s.isCancelled()) {
            // See blockingTurn: a barge-in costs the whole line, which is then what resumes.
            s.setUnspokenText(text);
        }
        return outcome;
    }

    /**
     * Whether the tools just run call for another model round: a data tool answered, and the
     * model has not yet been shown what it said.
     */
    private boolean wantsResultRound(DialogSession s, List<AssistantMessage.ToolCall> calls, String toolNote) {
        // A flow stage's tool result is read by the next stage (TurnRunner), not answered here.
        return dialogProperties.toolResultRounds() > 0 && toolNote != null && calls != null
                && !s.isLineGone() && !s.isEnded() && !s.isCancelled() && !s.isWaitingForCaller()
                && s.stageActionStep() <= 0
                // A tool started without waiting has no answer yet: a round would only read "started" back.
                && calls.stream().anyMatch(call -> DialogTools.isDataTool(call.name()) && !isScenarioTool(s, call.name())
                        && !s.isBackgroundTool(call.name()));
    }

    /**
     * Whether a tool call with no text is the flow working as drawn: the turn was a stage's own
     * job ("call it without the client hearing") and the stage the call is in now still has a
     * job for the model, so the next turn is already on its way (TurnRunner#scheduleStageAction)
     * and speaks if anything needs saying. Asking again for a line here only made the model
     * invent one — on a recorded call a goodbye, which closed the call before the CRM stages ran.
     */
    private static boolean isSilentStageStep(DialogSession s, boolean stageActionTurn) {
        StageDef stage = SystemPromptFactory.stageOf(s.scenario(), s.state());
        return stageActionTurn && !s.isEnded() && StageEntry.needsModelAction(stage)
                && s.stageActionStep() < StageEntry.countModelSteps(stage);
    }

    /**
     * Whether a stage's job turn that wrote nothing is better left silent because the floor is
     * the caller's: the agent's last line asked them something and the job was run without the
     * caller saying a word since. Asking the model again for a line here made it ask a second
     * question on top of the first — "Qaysi turar joy majmuamiz … ma'qul?" and then, with
     * nobody having spoken, "Qaysi turar joy majmuasini ko'rib chiqayotgan edingiz?".
     */
    static boolean isCallersTurnToAnswer(DialogSession s, boolean stageActionTurn) {
        String last = s.lastAgentText();
        return stageActionTurn && !s.isEnded() && last != null && last.strip().endsWith("?");
    }

    /**
     * Whether this turn is a stage's silent job (a tool to call, values to read) run while the
     * agent's own question still waits for the caller — so whatever the model writes in it is
     * not spoken. The job's purpose says "call it without the client hearing", and a recorded
     * call shows what the model wrote anyway: right after "Tushunarli, Mandarin House
     * majmuasidan qaraymiz. Necha xonali xonadon ma'qul kelyapti sizga?" came "Mandarin House
     * majmuamiz bo'yicha ma'lumotlarni qarayapman. O'zi necha xonali xonadon qidiryapsiz?".
     */
    private static boolean isMutedStageJob(DialogSession s, boolean stageActionTurn) {
        return StageEntry.isSilentJob(SystemPromptFactory.stageOf(s.scenario(), s.state()))
                && isCallersTurnToAnswer(s, stageActionTurn);
    }

    private static void logMutedStageJob(DialogSession s, String text) {
        if (text != null && !text.isBlank()) {
            log.info("[{}] silent stage job in {} wrote a line while the caller owes an answer — not spoken: {}",
                    s.channelId(), s.state(), text);
        }
    }

    /**
     * A scenario's own tool only records an outcome and answers with a confirmation —
     * nothing to answer with, so it is not worth a whole extra round trip.
     */
    private static boolean isScenarioTool(DialogSession s, String toolName) {
        // The agent's EXTRACT_VARIABLE functions record values the same way and answer the same confirmation.
        return s.scenario() != null && s.scenario().tools() != null
                && s.scenario().tools().stream().anyMatch(t -> t.name().equals(toolName))
                || AgentFunctionPlan.findExtractFunctions(s.agent()).stream()
                .anyMatch(function -> toolName.equals(function.name()));
    }

    /**
     * The messages so far plus what the model wrote this step and what its tools returned.
     *
     * <p>As plain text on purpose, never a replayed native tool call: Gemini 3 rejects the
     * replay without a thought_signature (see {@link TurnTools#buildOptions}).
     */
    private static List<Message> withToolResult(List<Message> messages, String stepText, String toolNote) {
        List<Message> next = new ArrayList<>(messages);
        if (stepText != null && !stepText.isEmpty()) {
            next.add(new AssistantMessage(stepText));
        }
        next.add(new UserMessage("[SYSTEM: Tool result: " + toolNote + "] Answer the client now using these "
                + "results. Do not repeat what you already said. If the result is an error or empty, say so "
                + "briefly and do not invent data."));
        return next;
    }

    private static String toolNames(List<AssistantMessage.ToolCall> calls) {
        return calls.stream().map(AssistantMessage.ToolCall::name).toList().toString();
    }

    private static String joinText(String first, String second) {
        return first.isEmpty() ? second : second.isEmpty() ? first : first + " " + second;
    }

    private void maybeSpeakPreToolSpeech(DialogSession s, List<AssistantMessage.ToolCall> calls, boolean alreadySpoken) {
        // A model that already spoke has its own waiting line; a filler on top would double it.
        if (alreadySpoken || s.isCancelled() || s.isEnded() || calls == null || calls.isEmpty()) {
            return;
        }
        // Nothing said yet means this is the greeting turn: "let me check" before the hello sounds
        // like the agent talking to itself.
        if (s.lastAgentText() == null) {
            return;
        }
        // The agent's function list can ask for a waiting line of its own ("talk while waiting"), which then
        // is said even where the installation's filler is off; a function that does not ask changes nothing.
        Map<String, AgentFunction> functions = AgentFunctionPlan.indexByModelName(s.agent(), s.tools());
        for (AssistantMessage.ToolCall call : calls) {
            if (SILENT_CONTROL_TOOLS.contains(call.name())) {
                continue;
            }
            AgentFunction function = functions.get(call.name());
            if (function != null && function.runsInBackground()) {
                continue; // not waited for, so there is no wait to announce
            }
            boolean functionSpeaks = function != null && function.speakDuringExecution();
            if (!dialogProperties.preToolSpeech() && !functionSpeaks) {
                continue;
            }
            String phrase = functionSpeaks ? function.executionMessage() : null;
            if (phrase == null && s.scenario() != null && s.scenario().tools() != null) {
                phrase = s.scenario().tools().stream()
                        .filter(t -> t.name().equals(call.name()))
                        .map(ToolDef::preToolSpeech)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
            }
            if ((phrase == null || phrase.isBlank()) && s.tools() != null) {
                // An agent-attached HTTP tool carries its own filler; calls come in under the sanitized name.
                phrase = s.tools().stream()
                        .filter(t -> HttpToolExecutor.sanitizeToolName(t.name()).equals(call.name()))
                        .map(t -> t.speech().preToolSpeech())
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
            }
            if ((phrase == null || phrase.isBlank()) && !isScenarioTool(s, call.name()) && s.stageActionStep() <= 0) {
                // A scenario tool only records a value and returns at once — "let me check" before
                // it announces a wait that never comes. A flow stage's job is silent by design and
                // the thinking sound covers it. Either speaks only when its own phrase is set.
                phrase = PreToolPhrases.forTool(call.name(), s.language());
            }
            if (phrase != null && !phrase.isBlank() && !isQuickTool(s, call.name())) {
                log.info("[{}] pre-tool speech before executing tool '{}': {}", s.channelId(), call.name(), phrase);
                speech.speakWaitingLine(s, phrase);
                break;
            }
        }
    }

    /**
     * Whether the tool has answered fast enough so far that a waiting line before it would announce
     * a wait the caller never has — "Hozir tekshirib ko'raman" before a 300 ms lookup. Measured
     * against the same perception threshold as the thinking filler ({@code filler-wait-threshold-ms});
     * a tool not run yet still gets its line, and 0 keeps every line.
     */
    private boolean isQuickTool(DialogSession s, String toolName) {
        int threshold = dialogProperties.fillerWaitThresholdMs();
        long expected = turnTools.findExpectedRunMs(s.companyId(), toolName);
        if (threshold > 0 && expected >= 0 && expected < threshold) {
            log.debug("[{}] no pre-tool speech for '{}': it takes about {} ms", s.channelId(), toolName, expected);
            return true;
        }
        return false;
    }

    /** Second attempt when a turn produced only tool calls: text, no tools. */
    private String retryForSpokenLine(DialogSession s, String system, List<Message> messages, String toolNote) {
        ChatResponse response = callWithDeadline(() -> clientFor(s).prompt()
                .system(system)
                .messages(spokenLineRetry(messages, toolNote))
                // Fast model, no tools — same reasoning as the streaming path above.
                .options(turnTools.buildOptions(s, List.of(), resolveFastModel(s)).mutate())
                .call()
                .chatResponse(), llmTimeout.callTimeoutMs());
        TokenUsage retryUsage = new TokenUsage();
        retryUsage.add(response);
        publishUsage(s, retryUsage);
        return extractText(response);
    }

    /**
     * The turn's messages plus the instruction to answer with the spoken line alone.
     *
     * <p>Appended rather than folded into the system message: changing the system
     * message would invalidate the cached prefix this whole design protects, and this
     * extra round trip is exactly the one worth keeping cheap.
     *
     * @param toolNote what the tools returned, so the model can react to a guardrail
     *                 that refused it (a promise dated in the past) — or null
     */
    private static List<Message> spokenLineRetry(List<Message> messages, String toolNote) {
        List<Message> retryMessages = new ArrayList<>(messages);
        if (toolNote != null) {
            retryMessages.add(new UserMessage("[SYSTEM: Tool result: " + toolNote + "]"));
        }
        retryMessages.add(new UserMessage("[SYSTEM: Now return only the text to be spoken to the "
                + "client. Do not call tools, do not write comments.]"));
        return retryMessages;
    }

    /**
     * What one drained stream produced — the same three facts a {@link TurnResult} carries.
     *
     * @param spokenText the sentences that reached the caller, joined; differs from {@code text}
     *                   when a sentence was refused
     */
    private record StreamedReply(String text, boolean spoken, boolean blocked, String spokenText) {
    }
}
