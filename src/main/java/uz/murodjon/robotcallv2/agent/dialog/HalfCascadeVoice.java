package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uz.murodjon.robotcallv2.agent.metrics.DialogMetrics;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.agent.session.CallMdc;
import uz.murodjon.robotcallv2.agent.tts.TtsRouter;
import uz.murodjon.robotcallv2.aiagent.domain.entity.PronunciationRule;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;

/**
 * The mouth of a {@code PipelineMode.HALF_CASCADE} call: the realtime engine's answer
 * arrives here as text while the engine is still producing it, is cut into sentences, and
 * each sentence is checked by the fact guard and spoken by our TTS voice. The engine's own
 * audio is never played.
 *
 * <p>Sentences are synthesized one after another on a single virtual thread, so they reach
 * the line in the order the engine said them. A barge-in bumps the generation: every
 * sentence of the abandoned answer — queued or half-synthesized — is dropped.
 */
final class HalfCascadeVoice {

    private static final Logger log = LoggerFactory.getLogger(HalfCascadeVoice.class);

    private final RealtimeDialogSession session;
    private final TtsRouter ttsRouter;
    private final DialogPolicy dialogPolicy;
    private final DialogMetrics metrics;
    private final String voiceId;
    private final EffectiveVoiceSettings style;
    private final List<PronunciationRule> pronunciationRules;
    /** Called with the queue position of the first audio of a reply — where truncation and latency measure from. */
    private final LongConsumer beginReply;

    private final ExecutorService speaker = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
    private final AtomicLong generation = new AtomicLong();
    /** Text of the current answer not yet cut into a sentence. Guarded by {@code this}. */
    private final StringBuilder pending = new StringBuilder();

    HalfCascadeVoice(RealtimeDialogSession session, TtsRouter ttsRouter, DialogPolicy dialogPolicy,
                     DialogMetrics metrics, String voiceId, EffectiveVoiceSettings style,
                     List<PronunciationRule> pronunciationRules, LongConsumer beginReply) {
        this.session = session;
        this.ttsRouter = ttsRouter;
        this.dialogPolicy = dialogPolicy;
        this.metrics = metrics;
        this.voiceId = voiceId;
        this.style = style;
        this.pronunciationRules = pronunciationRules != null ? pronunciationRules : List.of();
        this.beginReply = beginReply;
    }

    /** More of the engine's answer. Every complete sentence in it is queued for speech. */
    synchronized void append(String delta) {
        pending.append(delta);
        String sentence;
        while ((sentence = SentenceSplitter.takeSentence(pending)) != null) {
            queue(sentence);
        }
    }

    /** The engine finished its answer: whatever is left, however short, is spoken too. */
    synchronized void flush() {
        String rest = pending.toString().trim();
        pending.setLength(0);
        if (!rest.isEmpty()) {
            queue(rest);
        }
    }

    /** The caller took the floor: nothing of the current answer is spoken any more. */
    synchronized void cancel() {
        generation.incrementAndGet();
        pending.setLength(0);
    }

    /** Runs {@code action} once every sentence queued so far has reached the line. */
    void afterSpoken(Runnable action) {
        speaker.execute(action);
    }

    void close() {
        speaker.shutdownNow();
    }

    private void queue(String sentence) {
        long gen = generation.get();
        speaker.execute(() -> CallMdc.with(session.channelId(), () -> speak(sentence, gen)));
    }

    private void speak(String sentence, long gen) {
        if (gen != generation.get()) {
            return;
        }
        // The gate a REALTIME call does not have: a figure the call facts do not hold is
        // never said, rather than audited after the caller has heard it (§4.4).
        List<String> bad = dialogPolicy.findFactViolations(sentence, session.scenario(), session.context(),
                session.toolFigures());
        if (!bad.isEmpty()) {
            metrics.factGuardBlock();
            log.warn("[{}] fact guard blocked a half-cascade sentence: figures {} are not in the call facts",
                    session.channelId(), bad);
            return;
        }
        RtpEndpoint endpoint = session.endpoint();
        try {
            ttsRouter.synthesizeStreaming(sentence, session.language(), voiceId, style, pronunciationRules, pcm -> {
                if (gen != generation.get()) {
                    return;
                }
                if (!session.replyOpen()) {
                    beginReply.accept(endpoint.queuedSamples());
                }
                endpoint.enqueuePcm(pcm);
                session.touch();
            });
        } catch (RuntimeException e) {
            log.warn("[{}] half-cascade TTS failed, sentence skipped: {}", session.channelId(), e.getMessage());
        }
    }
}
