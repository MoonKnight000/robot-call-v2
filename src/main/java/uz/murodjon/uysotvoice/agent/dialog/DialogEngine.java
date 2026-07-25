package uz.murodjon.uysotvoice.agent.dialog;

import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import uz.murodjon.uysotvoice.agent.metrics.VoiceMetrics;
import uz.murodjon.uysotvoice.agent.record.CallRecordService;
import uz.murodjon.uysotvoice.agent.rtp.RtpEndpoint;
import uz.murodjon.uysotvoice.agent.tts.TtsProperties;
import uz.murodjon.uysotvoice.agent.tts.TtsRouter;
import uz.murodjon.uysotvoice.shared.dialog.DialogState;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The FSM + LLM dialog engine (PROJECT.md §4, §7.1, Stage 7). Owns one LLM
 * ChatClient (Spring AI OpenAI starter → Gemini via proxy) and a per-call
 * {@link DialogSession} registry. Each
 * client final transcript triggers a turn: build the state-specific system prompt,
 * call the LLM with the running history and per-session tools (which may advance the
 * FSM or record outcomes), then synthesize the reply and stream it to the caller.
 *
 * <p>Turns run on a virtual-thread worker so the blocking LLM/TTS calls never stall
 * the STT/RTP threads. A per-session {@code busy} flag serializes turns and drops
 * finals that arrive while a turn is still in flight. Barge-in, sentence-level TTS
 * streaming, and DB/CRM persistence come in later stages (§7.2, Stage 9).
 *
 * <p>Non-fatal without credentials: if no LLM ChatModel is available (no
 * {@code GEMINI_API_KEY}/{@code GEMINI_API_BASE_URL}), the app still runs and dialog is disabled.
 */
@Service
public class DialogEngine {

    private static final Logger log = LoggerFactory.getLogger(DialogEngine.class);

    /** Bootstraps the opening turn — the bot speaks first (there is no client input yet). */
    private static final String GREETING_BOOTSTRAP =
            "[TIZIM: Qo'ng'iroq ulandi, mijoz go'shakni ko'tardi. Rejaga muvofiq salomlashing.]";

    private final DialogProperties props;
    private final SystemPromptFactory promptFactory;
    private final TtsProperties ttsProps;
    private final TtsRouter ttsRouter;
    private final CallRecordService records;
    private final VoiceMetrics metrics;
    private final ObjectProvider<ChatModel> chatModelProvider;

    private final Map<String, DialogSession> sessions = new ConcurrentHashMap<>();
    private final ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();

    private volatile ChatClient chatClient;

    public DialogEngine(DialogProperties props,
                        SystemPromptFactory promptFactory,
                        TtsProperties ttsProps,
                        TtsRouter ttsRouter,
                        CallRecordService records,
                        VoiceMetrics metrics,
                        ObjectProvider<ChatModel> chatModelProvider) {
        this.props = props;
        this.promptFactory = promptFactory;
        this.ttsProps = ttsProps;
        this.ttsRouter = ttsRouter;
        this.records = records;
        this.metrics = metrics;
        this.chatModelProvider = chatModelProvider;
    }

    @PostConstruct
    public void init() {
        ChatModel model = chatModelProvider.getIfAvailable();
        if (model != null) {
            chatClient = ChatClient.create(model);
            log.info("Dialog engine ready (LLM model bean: {})", model.getClass().getSimpleName());
        } else {
            log.warn("Dialog engine has no LLM ChatModel (set GEMINI_API_KEY + GEMINI_API_BASE_URL); dialog disabled");
        }
    }

    public boolean available() {
        return props.enabled() && chatClient != null;
    }

    /**
     * Registers a conversation for {@code channelId} and drives the opening greeting.
     * No-op if dialog is disabled or the LLM is unavailable.
     *
     * @param hangup invoked once the conversation ends, to hang up the channel
     */
    public void startCall(String channelId, RtpEndpoint endpoint, CallContext context,
                          String language, Runnable hangup, Runnable transfer, long callAttemptId) {
        if (!available()) {
            log.debug("Dialog not started for {} (engine unavailable)", channelId);
            return;
        }
        DialogSession session = new DialogSession(channelId, language, context, endpoint, hangup, transfer, callAttemptId);
        sessions.put(channelId, session);
        log.info("Dialog started [{}] lang={} state={}", channelId, language, session.state());
        worker.submit(() -> advance(session, GREETING_BOOTSTRAP));
    }

    /** Feeds a final client transcript into the conversation as the next turn. */
    public void onClientFinal(String channelId, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        DialogSession session = sessions.get(channelId);
        if (session == null || session.isEnded()) {
            return;
        }
        worker.submit(() -> advance(session, text));
    }

    /**
     * Barge-in (PROJECT.md §7.2): the client started speaking. If the bot is mid-
     * utterance, silence it immediately (flush the RTP playback) and flag the turn so
     * the next LLM prompt knows it was interrupted. No-op if the bot is not speaking.
     */
    public void notifyBargeIn(String channelId) {
        DialogSession session = sessions.get(channelId);
        if (session == null || session.isEnded()) {
            return;
        }
        if (session.endpoint().isPlaying()) {
            session.endpoint().flushPlayback();
            session.setInterrupted(true);
            log.info("[{}] barge-in: bot silenced mid-utterance", channelId);
        }
    }

    /** Current disposition recorded by the dialog (nullable), read before teardown. */
    public Disposition disposition(String channelId) {
        DialogSession session = sessions.get(channelId);
        return session != null ? session.disposition() : null;
    }

    /** Drops the conversation state when the call tears down. */
    public void endCall(String channelId) {
        sessions.remove(channelId);
    }

    private void advance(DialogSession s, String clientText) {
        if (s.isEnded()) {
            return;
        }
        // Serialize turns; drop finals that arrive mid-turn (crude turn-taking until VAD, §7.2).
        if (!s.busy().compareAndSet(false, true)) {
            log.debug("[{}] turn in progress, dropping: {}", s.channelId(), clientText);
            return;
        }
        try {
            MDC.put("channelId", s.channelId());
            if (Duration.between(s.startedAt(), Instant.now()).getSeconds() > props.maxCallSeconds()) {
                closeOnLimit(s, "maksimal davomiylik");
                return;
            }
            if (s.incrementTurn() > props.maxTurns()) {
                closeOnLimit(s, "maksimal turn soni");
                return;
            }

            s.history().add(new UserMessage(clientText));
            String system = promptFactory.build(s);
            if (s.isInterrupted()) {
                // Tell the model it was cut off and where it stopped (§7.2 step 5).
                system += "\n\n[TIZIM: Mijoz siz gapirayotganda sizni bo'ldi. Siz shu yergacha aytgan edingiz: \""
                        + (s.lastAgentText() == null ? "" : s.lastAgentText())
                        + "\". Mijozning gapiga moslashing; butun gapni qaytadan boshlamang.]";
                s.setInterrupted(false);
            }
            DialogTools tools = new DialogTools(s);

            String reply;
            Timer.Sample llmSample = metrics.startTimer();
            try {
                reply = chatClient.prompt()
                        .system(system)
                        .messages(s.history())
                        .tools(tools)
                        .call()
                        .content();
                if (reply == null || reply.isBlank()) {
                    // Tool-only turn: the model called e.g. transitionTo and left the text
                    // empty, which would leave the caller listening to silence. The tools
                    // have already run, so ask once more for the spoken line alone.
                    log.warn("[{}] empty LLM reply in {} — asking again for the spoken line",
                            s.channelId(), s.state());
                    reply = chatClient.prompt()
                            .system(system + "\n\n[TIZIM: Endi faqat mijozga ovoz bilan aytiladigan "
                                    + "matnni qaytaring. Tool chaqirmang, izoh yozmang.]")
                            .messages(s.history())
                            .call()
                            .content();
                }
            } catch (Exception e) {
                metrics.llmError();
                log.warn("LLM turn failed [{}]: {}", s.channelId(), e.getMessage());
                return;
            } finally {
                metrics.stopLlmTurn(llmSample);
            }

            if ((reply == null || reply.isBlank()) && !s.isEnded()) {
                // Still nothing to say and the call is not over: speak a neutral line
                // rather than hand the caller silence.
                metrics.llmError();
                log.warn("[{}] no speakable text from the LLM in {}; using the fallback line",
                        s.channelId(), s.state());
                reply = fallbackLine(s);
            }

            if (reply != null && !reply.isBlank()) {
                s.history().add(new AssistantMessage(reply));
                s.setLastAgentText(reply); // remembered so barge-in can tell the model where it stopped
                log.info("[{}] AGENT ({}): {}", s.channelId(), s.state(), reply);
                records.addTranscript(s.callAttemptId(), "AGENT", reply, s.state().name(), offsetMs(s), null);
                long playMs = speak(s, reply);
                if (s.isEnded()) {
                    finishAfter(s, playMs);
                }
            } else if (s.isEnded()) {
                finishAfter(s, 0);
            }
        } finally {
            s.busy().set(false);
            MDC.remove("channelId");
        }
    }

    /**
     * Last resort when the LLM returns no text at all: the caller must hear something.
     * In GREETING that has to be the opening line (nobody has spoken yet); later on,
     * asking the client to repeat keeps the conversation alive.
     */
    private static String fallbackLine(DialogSession s) {
        boolean ru = s.language() != null && s.language().startsWith("ru");
        if (s.state() == DialogState.GREETING) {
            return ru
                    ? "Здравствуйте! Это автоматический голосовой сервис компании Uysot. Разговор записывается."
                    : "Assalomu alaykum! Bu Uysot kompaniyasining avtomatik ovozli xizmati. Suhbat yozib olinmoqda.";
        }
        return ru
                ? "Извините, я вас не расслышал. Повторите, пожалуйста."
                : "Uzr, sizni eshitolmadim. Iltimos, takrorlab ayting.";
    }

    /** Synthesizes {@code text} and streams it to the caller; returns audio length in ms. */
    private long speak(DialogSession s, String text) {
        if (!ttsProps.enabled()) {
            return 0;
        }
        try {
            short[] pcm = ttsRouter.synthesize(text, s.language());
            s.endpoint().playPcm(pcm);
            return pcm.length * 1000L / 8000L; // 8 kHz mono
        } catch (Exception e) {
            log.warn("TTS failed during dialog [{}]: {}", s.channelId(), e.getMessage());
            return 0;
        }
    }

    /**
     * Waits for the farewell audio to finish, then ends the call: transfer to a human
     * operator if the outcome is TRANSFERRED (§11.6), otherwise hang up.
     */
    private void finishAfter(DialogSession s, long playMs) {
        try {
            Thread.sleep(playMs + 500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Runnable action = (s.disposition() == Disposition.TRANSFERRED && s.transfer() != null)
                ? s.transfer() : s.hangup();
        if (action != null) {
            action.run();
        }
    }

    /** Live context for the operator screen after a transfer (null if unknown). */
    public OperatorSnapshot operatorSnapshot(String channelId) {
        DialogSession s = sessions.get(channelId);
        if (s == null) {
            return null;
        }
        CallContext c = s.context();
        return new OperatorSnapshot(
                channelId,
                c.clientName(),
                c.debtAmount() != null ? c.debtAmount().toString() : null,
                c.currency(),
                c.dueDate() != null ? c.dueDate().toString() : null,
                c.contractNumber(),
                s.state().name(),
                records.transcriptText(s.callAttemptId()));
    }

    private static int offsetMs(DialogSession s) {
        return (int) Duration.between(s.startedAt(), Instant.now()).toMillis();
    }

    private void closeOnLimit(DialogSession s, String reason) {
        log.info("[{}] closing dialog ({})", s.channelId(), reason);
        s.end(null);
        long ms = speak(s, "Vaqtingiz uchun rahmat, xayr.");
        finishAfter(s, ms);
    }

    @PreDestroy
    public void shutdown() {
        worker.shutdownNow();
    }
}
