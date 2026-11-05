package uz.murodjon.robotcallv2.agent.stt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.murodjon.robotcallv2.agent.audio.SpeechGate;
import uz.murodjon.robotcallv2.agent.audio.StreamingDownsampler;
import uz.murodjon.robotcallv2.agent.audio.StreamingUpsampler;
import uz.murodjon.robotcallv2.agent.audio.WidebandAudioListener;
import uz.murodjon.robotcallv2.agent.metrics.SttMetrics;
import uz.murodjon.robotcallv2.agent.turn.AgentQuestionCues;
import uz.murodjon.robotcallv2.agent.turn.TranscriptTurnCues;
import uz.murodjon.robotcallv2.agent.turn.TurnCue;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntConsumer;

/**
 * Feeds a call's decoded 8 kHz PCM into a streaming STT session, upsampling to
 * 16 kHz only when the provider is configured for it, and forwards transcripts to
 * the supplied {@link TranscriptListener} (Stage 5; wired to the dialog engine in
 * Stage 7).
 *
 * <p>When a {@link SpeechGate} is supplied, only audio the VAD considers speech (plus
 * its pre-roll and post-roll margins) reaches the provider — the rest is counted and
 * dropped. Providers bill per streamed second, and most of a call is not speech. The
 * gate is optional and fails open: no VAD, or a VAD error, means every frame is sent
 * exactly as before.
 *
 * <p>Includes an active audio replay buffer to prevent "lost turns" on mid-call gRPC
 * stream drops or reconnects: whenever a stream disconnects or stalls, recent in-flight
 * speech is retained and seamlessly replayed once the new stream opens.
 */
public class SttStreamBridge implements WidebandAudioListener, Closeable {

    private static final Logger log = LoggerFactory.getLogger(SttStreamBridge.class);

    /**
     * How long to wait before opening another stream after a failed attempt. Audio
     * arrives every 20 ms, so without a floor a provider that is simply down (a rejected
     * API key) would be asked for fifty new streams a second.
     */
    private static final long REOPEN_COOLDOWN_MS = 1000;

    /**
     * How long a freshly opened stream is allowed to be "not ready" before its frames
     * start being dropped.
     */
    private static final long READY_GRACE_MS = 2000;
    /**
     * How long before a provider's stream cap the stream starts being replaced at the next
     * pause. Wide enough that a caller who talks through most of it still leaves a gap.
     */
    private static final long STREAM_LIMIT_MARGIN_MS = 30_000;

    private final String channelId;
    /** Rate of the audio this bridge is fed: 8 kHz, or 16 kHz once a wideband leg says so ({@link #onWidebandAudio}). */
    private volatile int sourceSampleRate;
    /** {@link #sourceSampleRate} over 8 kHz — what the 8 kHz-sized replay buffer is scaled by. */
    private volatile int widebandFactor = 1;
    private final SpeechGate gate;
    private final SttMetrics metrics;
    /** Where another provider comes from when this one stops working; null disables failover. */
    private final SttProviderSelector selector;
    /** Replay buffer size, promoted-final window and failover threshold ({@code stt.stream.*}). */
    private final SttStreamProperties streamProperties;
    /**
     * The provider recognizing this call, and the rate it wants audio in. Not final: a
     * provider that keeps failing is replaced mid-call ({@link #failOver()}), and the
     * rate is the replacement's — sending 8 kHz audio to a provider expecting 16 kHz
     * transcribes as gibberish rather than failing.
     */
    private volatile SttProvider provider;
    private volatile int targetSampleRate;
    /**
     * Carries the resampling filter across frames for a provider whose rate is not the
     * source's; null when it takes the source rate as-is. Replaced whenever the audio it is
     * fed stops being one continuous stream — a new utterance after the gate, a reopened
     * stream, another provider — so no filter history crosses a gap. RTP thread only.
     */
    private volatile Resampling resampler;
    /**
     * Failures charged to the provider in hand. Cleared by a transcript and by nothing
     * else: a reopen that succeeds proves the socket opens, not that anything is being
     * recognized, and clearing it there let a provider that stalls every thirty seconds
     * reset its own count forever without the call ever moving off it.
     */
    private final AtomicInteger providerFailures = new AtomicInteger();
    private final String language;
    /** Other languages this caller may answer in ({@code voice-agent.stt.detect-languages}). */
    private final List<String> alternativeLanguages;
    private final TranscriptListener listener;
    /** Who the transcripts are for, once this bridge has had its say — the dialog side. */
    private final TranscriptListener downstream;
    /** The call's own hints ({@link SttHints#of}), before any boosted keywords are put in front. */
    private final List<String> callHints;
    /** {@link #callHints} behind the agent's boosted keywords — what a stage without its own list uses. */
    private final List<String> agentHints;
    /**
     * Words this call is likely to contain, for a provider that can be biased ({@link SttHints}).
     * Replaced by {@link #retune} when a stage brings keywords of its own.
     */
    private volatile List<String> hints;
    /**
     * The recognition model the agent asked for ({@code ai_agent.stt_model}), or null for the
     * provider's configured one. Cleared by {@link #failOver()}: a model name belongs to the
     * vendor it was chosen for, and Deepgram's would be rejected outright by Yandex.
     */
    private volatile String model;
    /** The model the call started with — what a stage without a model of its own goes back to. */
    private volatile String agentModel;
    /**
     * Set by {@link #retune} when a stage wants another model or other hints; the stream is
     * swapped on the audio thread's next frame, never on the caller of {@code retune}.
     */
    private volatile boolean retunePending;
    /** Told when an utterance is declared over, with the silence that closed it. */
    private final IntConsumer onUtteranceEnd;
    /** Whether this call ends its own utterances rather than letting the provider do it. */
    private final boolean externalEndpointing;
    private long maxUtteranceSamples;
    /** How long a stream may take audio without answering before it counts as stalled; 0 = never. */
    private final long responseTimeoutMs;

    /** Audio sent since the current utterance began — 0 while the caller is silent. */
    private long utteranceSamples;

    private final Deque<short[]> replayBuffer = new ArrayDeque<>();
    private int replayBufferedSamples = 0;
    /**
     * Guards the replay buffer and the stream swap. A lock rather than {@code synchronized}:
     * {@link #reopen()} opens a network stream while holding it, on the call's virtual
     * consumer thread, and blocking inside {@code synchronized} pins the carrier — during a
     * provider outage every call reopening at once stalled every virtual thread in the JVM.
     */
    private final ReentrantLock lock = new ReentrantLock();
    /** Whether the stream in hand has answered anything since it was opened. */
    private volatile boolean heardSinceOpen;
    /** Audio sent on the stream in hand since it was opened. RTP thread only. */
    private long sentSinceOpen;
    /** Whether the stream in hand has already been charged to {@link #providerFailures}. */
    private boolean chargedThisStream;
    /** Whether the last successful {@link #reopen()} fed the replay buffer to the new stream. */
    private boolean replayedLastReopen;

    private volatile SttSession session;
    /** Set by {@link #close()} — a call that has ended must not reopen anything. */
    private volatile boolean closed;
    private long lastReopenAttemptAt;
    /** When the provider last said anything at all — written from its own thread. */
    private volatile long lastResponseAt;
    /** Frames thrown away since the transport last accepted one. */
    private long droppedFrames;
    /** When the stream in hand was opened — what {@link #READY_GRACE_MS} is measured from. */
    private long streamOpenedAt;
    /** Whether this stream's transport has ever been ready; before that it is still coming up. */
    private volatile boolean everReady;

    /** Whether the previous frame was sent — only used to log gate transitions. */
    private volatile boolean streaming = true;

    // Promoting a late final (EndpointingProperties#finalGraceMs). Guarded by finalLock:
    // the provider's final and the grace timer race for the same utterance.
    private final Object finalLock = new Object();
    private final long finalGraceMs;
    /** The provider's last interim for the utterance in hand. */
    private volatile String lastInterim;
    /** An EOU has been sent and the provider has not answered it with a final yet. */
    private boolean finalPending;
    /** When the last interim was promoted, or 0 — the provider's late final is dropped for a while. */
    private volatile long promotedAt;

    /**
     * @param endpointing       when enabled <em>and</em> a gate is installed, this call decides
     *                          when the caller has finished and tells the provider.
     * @param responseTimeoutMs how long a stream may swallow audio without answering before
     *                          it is replaced ({@code voice-agent.stt.response-timeout-ms})
     * @param onUtteranceEnd    told, with the silence that produced it, every time this side
     *                          declares an utterance over. Null for a call nothing is timing
     * @param hints             words this call is likely to contain — the client's name, the
     *                          services they will be pointed at ({@link SttHints}).
     * @param selector          where a replacement provider comes from when this one keeps
     *                          failing. Null leaves the call on the provider it started with,
     *                          which is what an offline tool replaying a fixed recording wants
     * @param model             the recognition model this agent chose, or null for the provider's
     *                          own ({@code ai_agent.stt_model})
     */
    public SttStreamBridge(SttProvider provider, int targetSampleRate, int sourceSampleRate, String channelId,
                           String language, List<String> alternativeLanguages, TranscriptListener listener,
                           SpeechGate gate, SttMetrics metrics, EndpointingProperties endpointing,
                           int responseTimeoutMs, IntConsumer onUtteranceEnd, List<String> hints,
                           SttProviderSelector selector, String model, SttStreamProperties streamProperties) {
        this(provider, targetSampleRate, sourceSampleRate, channelId, language, alternativeLanguages, listener,
                gate, metrics, endpointing, responseTimeoutMs, onUtteranceEnd, hints, null, selector, model,
                streamProperties);
    }

    /**
     * @param keywords the agent's boosted keywords, put in front of {@code hints}; null or
     *                 empty for none. A stage may replace them later ({@link #retune})
     */
    public SttStreamBridge(SttProvider provider, int targetSampleRate, int sourceSampleRate, String channelId,
                           String language, List<String> alternativeLanguages, TranscriptListener listener,
                           SpeechGate gate, SttMetrics metrics, EndpointingProperties endpointing,
                           int responseTimeoutMs, IntConsumer onUtteranceEnd, List<String> hints,
                           List<String> keywords, SttProviderSelector selector, String model,
                           SttStreamProperties streamProperties) {
        this(provider, targetSampleRate, sourceSampleRate, channelId, language, alternativeLanguages, listener,
                gate, metrics, endpointing, responseTimeoutMs, onUtteranceEnd, hints, keywords, selector, model,
                streamProperties, null);
    }

    /**
     * @param preopened a stream opened at ring time with exactly the parameters this call would
     *                  open ({@link SttPrewarmRegistry#take}); adopted instead of opening one.
     *                  Null opens a stream as usual
     */
    public SttStreamBridge(SttProvider provider, int targetSampleRate, int sourceSampleRate, String channelId,
                           String language, List<String> alternativeLanguages, TranscriptListener listener,
                           SpeechGate gate, SttMetrics metrics, EndpointingProperties endpointing,
                           int responseTimeoutMs, IntConsumer onUtteranceEnd, List<String> hints,
                           List<String> keywords, SttProviderSelector selector, String model,
                           SttStreamProperties streamProperties, PreopenedStt preopened) {
        this.streamProperties = streamProperties;
        this.selector = selector;
        this.model = (model == null || model.isBlank()) ? null : model.trim();
        this.agentModel = this.model;
        this.channelId = channelId;
        this.callHints = hints == null ? List.of() : List.copyOf(hints);
        this.agentHints = keywords == null || keywords.isEmpty()
                ? this.callHints : SttHints.withKeywords(keywords, this.callHints);
        this.hints = this.agentHints;
        this.onUtteranceEnd = onUtteranceEnd != null ? onUtteranceEnd : ms -> { };
        this.targetSampleRate = targetSampleRate;
        this.resampler = resamplerFor(sourceSampleRate, targetSampleRate);
        this.sourceSampleRate = sourceSampleRate;
        this.gate = gate;
        this.metrics = metrics;
        this.provider = provider;
        this.language = language;
        this.alternativeLanguages = alternativeLanguages == null ? List.of() : List.copyOf(alternativeLanguages);
        this.downstream = listener;
        this.listener = (text, isFinal, confidence) -> {
            lastResponseAt = System.currentTimeMillis();
            providerFailures.set(0);
            heardSinceOpen = true;
            if (closed) {
                log.warn("[{}] transcript arrived after the call ended, ignored: {}", channelId, text);
                return;
            }
            if (isFinal) {
                clearReplayBuffer();
                text = claimFinal(text);
                if (text == null) {
                    return; // already answered from the last interim
                }
            } else {
                if (gate != null) {
                    gate.onInterimTranscript(text);
                }
                synchronized (finalLock) {
                    lastInterim = text;
                }
            }
            if (listener != null) {
                listener.onTranscript(text, isFinal, confidence);
            }
        };
        this.externalEndpointing = endpointing != null && endpointing.enabled() && gate != null;
        this.finalGraceMs = externalEndpointing ? Math.max(0, endpointing.finalGraceMs()) : 0;
        this.maxUtteranceSamples = externalEndpointing
                ? (long) Math.max(1000, endpointing.maxUtteranceMs()) * sourceSampleRate / 1000
                : Long.MAX_VALUE;
        this.responseTimeoutMs = Math.max(0, responseTimeoutMs);
        this.lastResponseAt = System.currentTimeMillis();
        this.streamOpenedAt = this.lastResponseAt;
        if (preopened != null) {
            preopened.forwarder().bind(this.listener);
            this.session = preopened.session();
        } else {
            this.session = provider.startStream(language, this.alternativeLanguages, this.listener, externalEndpointing, this.hints, this.model);
        }
    }

    /**
     * The caller at 16 kHz, from a wideband leg — given instead of {@link #onAudio}, from the
     * first frame of the call on. The first frame switches this bridge to that rate for good:
     * pre-roll, replay buffer and utterance cap are re-sized, and a 16 kHz provider gets the
     * audio untouched instead of an upsampled telephone copy.
     */
    @Override
    public void onWidebandAudio(short[] pcm, int length) {
        if (widebandFactor == 1) {
            widebandFactor = 2;
            sourceSampleRate = 16000;
            if (maxUtteranceSamples != Long.MAX_VALUE) {
                maxUtteranceSamples *= widebandFactor;
            }
            utteranceSamples *= widebandFactor;
            if (gate != null) {
                gate.widenPreRoll(widebandFactor);
            }
            clearReplayBuffer();
            resampler = resamplerFor(sourceSampleRate, targetSampleRate);
            log.info("[{}] STT fed 16 kHz from a wideband leg ({} wants {} Hz)", channelId,
                    provider.name(), targetSampleRate);
        }
        onAudio(pcm, length);
    }

    @Override
    public void onAudio(short[] pcm, int length) {
        if (retunePending) {
            applyRetune();
        }
        if (gate != null && !gate.isOpen()) {
            gate.buffer(pcm, length);
            metrics.sttAudioSkipped(seconds(length));
            if (streaming) {
                streaming = false;
                if (gate.lastCloseWasNoise()) {
                    log.info("[{}] VAD heard speech the recognizer did not transcribe for {} ms — gate closed as noise",
                            channelId, utteranceSamples * 1000 / sourceSampleRate);
                } else {
                    // One line per turn end: which wait or model ended it (model-driven turn plan, phase 0).
                    log.info("[{}] turn closed after {} ms streamed: {}", channelId,
                            utteranceSamples * 1000 / sourceSampleRate, gate.lastCloseReason());
                }
                endUtterance();
                log.debug("[{}] STT stream gated (silence)", channelId);
            } else if (isNearStreamLimit()) {
                rotateStream();
            }
            return;
        }
        if (gate != null && !streaming) {
            streaming = true;
            // The caller is speaking again, so the next final is theirs, not a late part of the
            // utterance answered from its interim (claimFinal).
            synchronized (finalLock) {
                promotedAt = 0;
            }
            resampler = resamplerFor(sourceSampleRate, targetSampleRate);
            log.debug("[{}] STT stream resumed (speech)", channelId);
            short[] preRoll = gate.drainPreRoll();
            if (preRoll != null) {
                bufferForReplay(preRoll, preRoll.length);
                send(preRoll, preRoll.length);
            }
        }
        bufferForReplay(pcm, length);
        send(pcm, length);
    }

    private void bufferForReplay(short[] pcm, int length) {
        short[] copy = Arrays.copyOf(pcm, length);
        lock.lock();
        try {
            replayBuffer.addLast(copy);
            replayBufferedSamples += length;
            while (replayBufferedSamples > streamProperties.replayBufferSamples() * widebandFactor && !replayBuffer.isEmpty()) {
                short[] removed = replayBuffer.removeFirst();
                replayBufferedSamples -= removed.length;
            }
        } finally {
            lock.unlock();
        }
    }

    private void clearReplayBuffer() {
        lock.lock();
        try {
            replayBuffer.clear();
            replayBufferedSamples = 0;
        } finally {
            lock.unlock();
        }
    }

    private void send(short[] pcm, int length) {
        SttSession current = session;
        if (!current.isAlive()) {
            if (!reopen()) {
                return; // still no stream — the frame waits in the replay buffer
            }
            if (replayedLastReopen) {
                // The replay that fed the new stream already held this frame; sending it
                // again as well doubled it at the seam.
                return;
            }
        }
        if (session.isReady()) {
            everReady = true;
        } else if (everReady || System.currentTimeMillis() - streamOpenedAt >= READY_GRACE_MS) {
            metrics.sttAudioSkipped(seconds(length));
            if (droppedFrames++ == 0) {
                log.warn("[{}] STT transport is not accepting audio — buffering for replay", channelId);
            }
            return;
        }
        if (droppedFrames > 0) {
            log.warn("[{}] STT transport accepting audio again — {} frame(s) buffered/dropped", channelId, droppedFrames);
            droppedFrames = 0;
        }
        sendDirect(pcm, length);
    }

    private void sendDirect(short[] pcm, int length) {
        SttSession current = session;
        if (current == null || !current.isAlive()) {
            return;
        }
        short[] samples;
        int len;
        Resampling up = resampler;
        if (up != null) {
            samples = up.push(pcm, length);
            len = samples.length;
        } else {
            samples = pcm;
            len = length;
        }
        try {
            current.sendAudio(toLittleEndian(samples, len));
            metrics.sttAudioSent(seconds(length));
            utteranceSamples += length;
            sentSinceOpen += length;
            if (utteranceSamples >= maxUtteranceSamples) {
                log.warn("[{}] utterance ran past {} ms — forcing end of utterance",
                        channelId, maxUtteranceSamples * 1000 / sourceSampleRate);
                endUtterance();
            }
            dropStalledStream();
        } catch (Exception e) {
            log.warn("STT send failed [{}]: {}", channelId, e.getMessage());
        }
    }

    /**
     * Tell the recognizer the caller has finished, and start counting a new utterance.
     */
    private void endUtterance() {
        boolean spoke = utteranceSamples > 0;
        utteranceSamples = 0;
        clearReplayBuffer();
        if (!externalEndpointing || !spoke) {
            return;
        }
        SttSession current = session;
        if (!current.isAlive()) {
            return;
        }
        try {
            current.endUtterance();
            metrics.sttUtteranceEnded();
            metrics.sttEndpointingHangover(gate.hangoverMs());
            onUtteranceEnd.accept(gate.lastCloseWaitMs());
        } catch (Exception e) {
            log.warn("[{}] end-of-utterance signal failed: {}", channelId, e.getMessage());
            return;
        }
        if (finalGraceMs > 0) {
            synchronized (finalLock) {
                finalPending = true;
            }
            Thread.ofVirtual().name("stt-final-grace-" + channelId).start(this::promoteInterimAfterGrace);
        }
    }

    /**
     * Give the provider {@link #finalGraceMs} to answer the EOU with a final, then take
     * its last interim as the final instead.
     *
     * <p>Measured on recorded calls: the gate closed 500-700 ms after the caller's last
     * word and SpeechKit's final came another 500-1100 ms after that, saying what the
     * last interim had said all along. The whole of that second sat in front of every
     * reply. The provider's own final still comes; {@link #claimFinal} drops it.
     */
    private void promoteInterimAfterGrace() {
        try {
            Thread.sleep(finalGraceMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        String text;
        synchronized (finalLock) {
            if (!finalPending || closed || lastInterim == null || lastInterim.isBlank()) {
                return; // the final made it in time, or there is nothing to promote yet
            }
            if (TranscriptTurnCues.judge(lastInterim) == TurnCue.INCOMPLETE) {
                // "aa", "pulni", "men" — an interim that stops there is the one most likely
                // to be revised, and answering it answers half a sentence. The provider's
                // own final is still pending and will be taken as usual.
                return;
            }
            text = lastInterim;
            lastInterim = null;
            finalPending = false;
            promotedAt = System.currentTimeMillis();
        }
        metrics.sttFinalPromoted();
        // DEBUG: the text is what the caller said, and INFO goes to a two-week disk log.
        log.debug("[{}] no final {} ms after the EOU — using the last interim: {}", channelId, finalGraceMs, text);
        clearReplayBuffer();
        if (downstream != null) {
            downstream.onTranscript(text, true, 0f);
        }
    }

    /**
     * Whether a final from the provider is still wanted, or was already delivered from
     * the last interim. A dropped one is logged with both texts: the difference between
     * them says whether the grace is too short.
     *
     * @return the text to deliver as the final, or {@code null} when it is dropped
     */
    private String claimFinal(String text) {
        synchronized (finalLock) {
            String interim = lastInterim;
            finalPending = false;
            lastInterim = null;
            boolean stale = promotedAt != 0
                    && System.currentTimeMillis() - promotedAt < streamProperties.promotedFinalTtlMs();
            if (!stale) {
                String completed = completeWithInterim(text, interim);
                // The interim's tail may still come as a final of its own — it was said already.
                promotedAt = completed.equals(text) ? 0 : System.currentTimeMillis();
                if (!completed.equals(text)) {
                    log.debug("[{}] final covered only the start of the last interim, kept the interim: '{}' -> '{}'",
                            channelId, text, completed);
                }
                return completed;
            }
            // Kept, not cleared: the provider may answer one utterance in more than one final.
            // Clearing on the first let the second through as a new utterance — "vaaleykum
            // assalom … allo allo" was answered from the interim, then SpeechKit's last segment
            // came as "алло", and the bot spoke a second reply straight after the first.
            // New speech ends this instead (onAudio), and so does the TTL.
        }
        log.debug("[{}] late final dropped, already answered from the interim: {}", channelId, text);
        return null;
    }

    /**
     * The final, or the last interim when the final says only its first words. At the EOU
     * SpeechKit may finalize just the first fragment of a long utterance and send the rest
     * as an empty final: "мен оны карап кетем да" came as the final of an interim that went
     * on "… частный сек…". The speculative reply had answered the whole interim, while the
     * history and the transcript kept only the start.
     */
    static String completeWithInterim(String finalText, String interim) {
        if (interim == null || interim.isBlank()) {
            return finalText;
        }
        String head = normalizeForCompare(finalText);
        String whole = normalizeForCompare(interim);
        return !head.isEmpty() && whole.startsWith(head + " ") ? interim.strip() : finalText;
    }

    private static String normalizeForCompare(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}'ʻʼ‘’ ]", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private void dropStalledStream() {
        if (responseTimeoutMs == 0 || System.currentTimeMillis() - lastResponseAt < responseTimeoutMs) {
            return;
        }
        lastResponseAt = System.currentTimeMillis();
        log.warn("[{}] {} took audio for {} ms without a single transcript — dropping the stream",
                channelId, provider.name(), responseTimeoutMs);
        metrics.sttError();
        // Charged to the provider, not the stream. A vendor that accepts audio and answers
        // nothing passes every liveness check there is, so reopening it forever is exactly
        // what this used to do — and a call spent listening to a recognizer that never
        // speaks is indistinguishable, from the caller's side, from a bot that ignores them.
        providerFailures.incrementAndGet();
        chargedThisStream = true;
        try {
            session.close();
        } catch (Exception e) {
            log.debug("[{}] closing the stalled STT session failed: {}", channelId, e.getMessage());
        }
    }

    /**
     * Replace a stream that is no longer carrying audio.
     */
    private boolean reopen() {
        lock.lock();
        try {
            replayedLastReopen = false;
            SttSession current = session;
            if (current.isAlive()) {
                return true;
            }
            if (closed) {
                return false;
            }
            long now = System.currentTimeMillis();
            if (now - lastReopenAttemptAt < REOPEN_COOLDOWN_MS) {
                return false;
            }
            lastReopenAttemptAt = now;
            // A stream that took audio and died without answering a word is the provider
            // failing whether or not it said so — a refused connection, a 401, an immediate
            // UNAVAILABLE. Before this only a stall counted, so a hard outage never reached
            // the failover below and the call stayed deaf to the end. Charged once per
            // stream: a stall dropStalledStream already counted is not counted again.
            if (!chargedThisStream && sentSinceOpen > 0 && !heardSinceOpen) {
                providerFailures.incrementAndGet();
                chargedThisStream = true;
            }
            try {
                current.close();
            } catch (Exception e) {
                log.debug("[{}] closing the dead STT session failed: {}", channelId, e.getMessage());
            }
            if (current.isRejected()) {
                // Refused, not lost: the same stream is refused again. Call 12 retried Yandex with
                // a Gemini model id until the caller's first answer was gone. The model is the
                // likeliest cause, so the provider's own goes first; refused without one too,
                // the provider is what fails and the call moves on at once.
                if (model != null) {
                    log.warn("[{}] {} refused the stream with model {} — reopening on its own model",
                            channelId, provider.name(), model);
                    model = null;
                    agentModel = null;
                } else {
                    providerFailures.set(Math.max(providerFailures.get(), streamProperties.failoverAfterFailures()));
                }
            }
            if (providerFailures.get() >= streamProperties.failoverAfterFailures()) {
                failOver();
            }
            try {
                openStream(now);
                log.warn("[{}] STT stream had died — reopened on {}", channelId, provider.name());
                return true;
            } catch (Exception e) {
                // A reopen that fails outright is this provider failing too. Uncounted, a
                // vendor refusing every connection was retried once a second for the whole
                // call and the failover never came.
                providerFailures.incrementAndGet();
                metrics.sttError();
                log.warn("[{}] STT stream reopen failed ({} failure(s) on {}): {}",
                        channelId, providerFailures.get(), provider.name(), e.getMessage());
                return false;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Open a fresh stream on the provider in hand, with the current model and hints, and
     * replay the speech still waiting for a final into it. Lock held by the caller.
     */
    private void openStream(long now) {
        utteranceSamples = 0;
        resampler = resamplerFor(sourceSampleRate, targetSampleRate);
        session = provider.startStream(language, alternativeLanguages, this.listener, externalEndpointing, hints, model);
        lastResponseAt = now;
        streamOpenedAt = now;
        everReady = false;
        droppedFrames = 0;
        heardSinceOpen = false;
        sentSinceOpen = 0;
        chargedThisStream = false;

        // Replay buffered audio into the fresh stream so speech mid-sentence is not lost!
        if (!replayBuffer.isEmpty()) {
            log.info("[{}] replaying {} ms of speech into reopened STT stream to prevent lost turn",
                    channelId, replayBufferedSamples * 1000 / sourceSampleRate);
            List<short[]> toReplay = new ArrayList<>(replayBuffer);
            for (short[] chunk : toReplay) {
                sendDirect(chunk, chunk.length);
            }
            replayedLastReopen = true;
        }
    }

    /** The vendor recognizing this call right now — it changes on {@link #failOver()}. */
    public String providerName() {
        return provider.name();
    }

    /**
     * Hand the call's speech gate the wait the agent or its current stage asked for
     * ({@link SpeechGate#setResponseWaitMs}); null restores the configured one.
     *
     * @return false when this call has no gate, so the wait cannot be applied
     */
    public boolean setResponseWaitMs(Integer responseWaitMs) {
        if (gate == null) {
            return false;
        }
        gate.setResponseWaitMs(responseWaitMs);
        return true;
    }

    /** Tell the call's speech gate what the speculative reply made of an interim ({@link SpeechGate#setLlmTurnCue}). */
    public void setLlmTurnCue(String transcript, TurnCue cue) {
        if (gate != null) {
            gate.setLlmTurnCue(transcript, cue);
        }
    }

    /**
     * Tell the call's speech gate what the agent just said: the kind of answer it asked for
     * ({@link SpeechGate#setAgentQuestion}) and the line itself ({@link SpeechGate#setAgentLine}).
     */
    public void setAgentLine(String agentLine) {
        if (gate != null) {
            gate.setAgentQuestion(AgentQuestionCues.classify(agentLine));
            gate.setAgentLine(agentLine);
        }
    }

    /**
     * Point the recognizer at what the current stage asks for. A stream cannot change its
     * model or hints once open, so a change means a new stream — opened on the audio
     * thread's next frame with the unanswered speech replayed, never here: the caller is
     * the dialog, and opening a stream is a network round trip.
     *
     * @param forProvider   the provider {@code stageModel} was resolved for; on any other
     *                      (the call failed over meanwhile) the stage model is ignored
     * @param stageModel    the stage's model, or null for the one the call started with
     * @param stageKeywords the stage's boosted keywords, replacing the agent's (empty clears
     *                      them), or null for the agent's
     * @return whether the stream is going to be reopened
     */
    public boolean retune(String forProvider, String stageModel, List<String> stageKeywords) {
        lock.lock();
        try {
            if (closed) {
                return false;
            }
            String nextModel = stageModel != null && !stageModel.isBlank() && provider.name().equals(forProvider)
                    ? stageModel.trim() : agentModel;
            List<String> nextHints = stageKeywords == null ? agentHints
                    : stageKeywords.isEmpty() ? callHints : SttHints.withKeywords(stageKeywords, callHints);
            if (Objects.equals(nextModel, model) && nextHints.equals(hints)) {
                return false;
            }
            model = nextModel;
            hints = nextHints;
            retunePending = true;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether the stream in hand is within {@link #STREAM_LIMIT_MARGIN_MS} of the provider's
     * own cap ({@link SttProvider#maxStreamSeconds}) and nothing is still owed on it.
     */
    private boolean isNearStreamLimit() {
        int maxSeconds = provider.maxStreamSeconds();
        if (maxSeconds <= 0 || closed) {
            return false;
        }
        synchronized (finalLock) {
            if (finalPending) {
                return false; // the last utterance's final is still on its way on this stream
            }
        }
        return System.currentTimeMillis() - streamOpenedAt >= maxSeconds * 1000L - STREAM_LIMIT_MARGIN_MS;
    }

    /**
     * Replace a stream about to hit the provider's cap, while the caller is silent. Left to
     * the cap, it ends wherever it happens to be — mid-sentence, the words then recovered
     * only by the replay after a reopen. Audio thread only.
     */
    private void rotateStream() {
        lock.lock();
        try {
            // Closed on purpose, not failing: not charged to the provider.
            chargedThisStream = true;
            try {
                session.close();
            } catch (Exception e) {
                log.debug("[{}] closing the STT session before rotation failed: {}", channelId, e.getMessage());
            }
            openStream(System.currentTimeMillis());
            log.info("[{}] STT stream rotated on {} ahead of its {} s limit", channelId, provider.name(),
                    provider.maxStreamSeconds());
        } catch (Exception e) {
            // The session in hand is closed: the next frame reopens it the usual way.
            metrics.sttError();
            log.warn("[{}] STT stream rotation failed on {}: {}", channelId, provider.name(), e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /** Swap the stream for one with the model and hints {@link #retune} chose. Audio thread only. */
    private void applyRetune() {
        lock.lock();
        try {
            retunePending = false;
            if (closed) {
                return;
            }
            // Closed on purpose, not failing: not charged to the provider.
            chargedThisStream = true;
            try {
                session.close();
            } catch (Exception e) {
                log.debug("[{}] closing the STT session before a retune failed: {}", channelId, e.getMessage());
            }
            openStream(System.currentTimeMillis());
            log.info("[{}] STT stream reopened on {} for the new stage (model={}, {} hint(s))",
                    channelId, provider.name(), model != null ? model : "default", hints.size());
        } catch (Exception e) {
            // The session in hand is closed: the next frame reopens it the usual way.
            metrics.sttError();
            log.warn("[{}] STT stream retune failed on {}: {}", channelId, provider.name(), e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Move the call to another vendor after {@code stt.stream.failover-after-failures} failures of
     * the one in hand. Called from {@link #reopen()} with its lock held, so the next
     * stream is opened on whatever this leaves behind.
     *
     * <p>Deliberately one-way and unconditional: there is no going back to the failed
     * provider later in the call. A vendor that has failed twice in one call is having an
     * incident, and a bot that flips between recognizers every reopen would spend the
     * call switching rather than listening. The next call starts on the configured
     * provider again, because {@code providerFailures} is per bridge and a bridge is per
     * call — nothing here is remembered past the hangup, which is the right scope for an
     * outage this side cannot see the end of.
     */
    private void failOver() {
        if (selector == null) {
            return;
        }
        SttProvider replacement = selector.findFallback(provider, candidate -> candidate.supports(language));
        if (replacement == null) {
            // Said once per failure round, not per frame: reopen() is rate-limited by
            // REOPEN_COOLDOWN_MS, and this is the line that explains a call going quiet.
            log.warn("[{}] {} has failed {} times and this build has no other STT provider "
                            + "configured for {} — the call stays on it",
                    channelId, provider.name(), providerFailures.get(), language);
            providerFailures.set(0);
            return;
        }
        log.error("[{}] STT failing over: {} failed {} times, switching to {}",
                channelId, provider.name(), providerFailures.get(), replacement.name());
        metrics.sttFailover(provider.name(), replacement.name());
        provider = replacement;
        targetSampleRate = replacement.sampleRate();
        model = null;
        agentModel = null;
        providerFailures.set(0);
    }

    /**
     * The filter from the audio this bridge is fed to the provider's rate. From 8 kHz: a
     * band-limited upsampler for 16/24 kHz. From 16 kHz (a wideband leg): nothing for a
     * 16 kHz provider — the point of the leg — and for one that failed over to 8 or 24 kHz,
     * down to 8 kHz first, which is no worse than a telephone leg. Null for a rate there is
     * no filter for — sent as-is, as it always was.
     */
    private static Resampling resamplerFor(int sourceRate, int targetRate) {
        if (sourceRate == 16000) {
            if (targetRate == 16000) {
                return null;
            }
            StreamingDownsampler down = StreamingDownsampler.from16kTo8k();
            Resampling up = resamplerFor(8000, targetRate);
            if (up == null) {
                return down::push;
            }
            return (pcm, length) -> {
                short[] narrow = down.push(pcm, length);
                return up.push(narrow, narrow.length);
            };
        }
        StreamingUpsampler up = targetRate == 16000 || targetRate == 24000 ? StreamingUpsampler.from8kTo(targetRate) : null;
        return up == null ? null : up::push;
    }

    /** One stateful resampling filter; RTP thread only. */
    @FunctionalInterface
    private interface Resampling {
        short[] push(short[] pcm, int length);
    }

    /** Duration of a frame at the source rate — what the provider bills, upsampled or not. */
    private double seconds(int samples) {
        return (double) samples / sourceSampleRate;
    }

    @Override
    public void close() {
        lock.lock();
        try {
            closed = true;
            clearReplayBuffer();
            try {
                session.close();
            } catch (Exception e) {
                log.warn("Failed to close STT session [{}]: {}", channelId, e.getMessage());
            }
        } finally {
            lock.unlock();
        }
    }

    private static byte[] toLittleEndian(short[] samples, int length) {
        byte[] bytes = new byte[length * 2];
        for (int i = 0; i < length; i++) {
            bytes[i * 2] = (byte) (samples[i] & 0xFF);
            bytes[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
        }
        return bytes;
    }
}
