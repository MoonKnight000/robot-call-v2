package uz.murodjon.robotcallv2.agent.stt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uz.murodjon.robotcallv2.agent.audio.AudioListener;
import uz.murodjon.robotcallv2.agent.audio.Resampler;
import uz.murodjon.robotcallv2.agent.audio.SpeechGate;
import uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics;

import java.io.Closeable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
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
public class SttStreamBridge implements AudioListener, Closeable {

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

    /** Circular replay buffer to recover speech frames lost during gRPC drops (~2 seconds @ 8kHz). */
    private static final int MAX_REPLAY_SAMPLES = 16000;

    /**
     * How long after a promoted final the provider's own final for the same utterance
     * is still expected, and dropped. Past this a final belongs to the next utterance.
     */
    private static final long PROMOTED_FINAL_TTL_MS = 2500;

    /**
     * Failures of one provider — a stream that would not open, or one that took audio
     * without ever answering — before the call is moved to another vendor. Two, not one:
     * a single dropped stream is ordinary and the reopen above recovers it, and switching
     * vendors mid-sentence costs the recognizer whatever context it had built up.
     */
    private static final int FAILOVER_AFTER_FAILURES = 2;

    private final String channelId;
    private final int sourceSampleRate;
    private final SpeechGate gate;
    private final VoiceMetrics metrics;
    /** Where another provider comes from when this one stops working; null disables failover. */
    private final SttProviderSelector selector;
    /**
     * The provider recognizing this call, and the rate it wants audio in. Not final: a
     * provider that keeps failing is replaced mid-call ({@link #failOver()}), and the
     * rate is the replacement's — sending 8 kHz audio to a provider expecting 16 kHz
     * transcribes as gibberish rather than failing.
     */
    private volatile SttProvider provider;
    private volatile int targetSampleRate;
    /**
     * Failures charged to the provider in hand. Cleared by a transcript and by nothing
     * else: a reopen that succeeds proves the socket opens, not that anything is being
     * recognized, and clearing it there let a provider that stalls every thirty seconds
     * reset its own count forever without the call ever moving off it.
     */
    private volatile int providerFailures;
    private final String language;
    /** Other languages this caller may answer in ({@code voice-agent.stt.detect-languages}). */
    private final List<String> alternativeLanguages;
    private final TranscriptListener listener;
    /** Who the transcripts are for, once this bridge has had its say — the dialog side. */
    private final TranscriptListener downstream;
    /** Words this call is likely to contain, for a provider that can be biased ({@link SttHints}). */
    private final List<String> hints;
    /** Told when an utterance is declared over, with the silence that closed it. */
    private final IntConsumer onUtteranceEnd;
    /** Whether this call ends its own utterances rather than letting the provider do it. */
    private final boolean externalEndpointing;
    private final long maxUtteranceSamples;
    /** How long a stream may take audio without answering before it counts as stalled; 0 = never. */
    private final long responseTimeoutMs;

    /** Audio sent since the current utterance began — 0 while the caller is silent. */
    private long utteranceSamples;

    private final Deque<short[]> replayBuffer = new ArrayDeque<>();
    private int replayBufferedSamples = 0;

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
    private boolean everReady;

    /** Whether the previous frame was sent — only used to log gate transitions. */
    private boolean streaming = true;

    // Promoting a late final (EndpointingProperties#finalGraceMs). Guarded by finalLock:
    // the provider's final and the grace timer race for the same utterance.
    private final Object finalLock = new Object();
    private final long finalGraceMs;
    /** The provider's last interim for the utterance in hand. */
    private String lastInterim;
    /** An EOU has been sent and the provider has not answered it with a final yet. */
    private boolean finalPending;
    /** When the last interim was promoted, or 0 — the provider's late final is dropped for a while. */
    private long promotedAt;

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
     */
    public SttStreamBridge(SttProvider provider, int targetSampleRate, int sourceSampleRate, String channelId,
                           String language, List<String> alternativeLanguages, TranscriptListener listener,
                           SpeechGate gate, VoiceMetrics metrics, EndpointingProperties endpointing,
                           int responseTimeoutMs, IntConsumer onUtteranceEnd, List<String> hints,
                           SttProviderSelector selector) {
        this.selector = selector;
        this.channelId = channelId;
        this.hints = hints == null ? List.of() : List.copyOf(hints);
        this.onUtteranceEnd = onUtteranceEnd != null ? onUtteranceEnd : ms -> { };
        this.targetSampleRate = targetSampleRate;
        this.sourceSampleRate = sourceSampleRate;
        this.gate = gate;
        this.metrics = metrics;
        this.provider = provider;
        this.language = language;
        this.alternativeLanguages = alternativeLanguages == null ? List.of() : List.copyOf(alternativeLanguages);
        this.downstream = listener;
        this.listener = (text, isFinal, confidence) -> {
            lastResponseAt = System.currentTimeMillis();
            providerFailures = 0;
            if (closed) {
                log.warn("[{}] transcript arrived after the call ended, ignored: {}", channelId, text);
                return;
            }
            if (isFinal) {
                clearReplayBuffer();
                if (!claimFinal(text)) {
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
        this.session = provider.startStream(language, this.alternativeLanguages, this.listener, externalEndpointing, this.hints);
    }

    @Override
    public void onAudio(short[] pcm, int length) {
        if (gate != null && !gate.isOpen()) {
            gate.buffer(pcm, length);
            metrics.sttAudioSkipped(seconds(length));
            if (streaming) {
                streaming = false;
                endUtterance();
                log.debug("[{}] STT stream gated (silence)", channelId);
            }
            return;
        }
        if (gate != null && !streaming) {
            streaming = true;
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

    private synchronized void bufferForReplay(short[] pcm, int length) {
        short[] copy = Arrays.copyOf(pcm, length);
        replayBuffer.addLast(copy);
        replayBufferedSamples += length;
        while (replayBufferedSamples > MAX_REPLAY_SAMPLES && !replayBuffer.isEmpty()) {
            short[] removed = replayBuffer.removeFirst();
            replayBufferedSamples -= removed.length;
        }
    }

    private synchronized void clearReplayBuffer() {
        replayBuffer.clear();
        replayBufferedSamples = 0;
    }

    private void send(short[] pcm, int length) {
        SttSession current = session;
        if (!current.isAlive() && !reopen()) {
            // Still no stream - frame already saved in replayBuffer for replay upon reconnect
            return;
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
        if (targetSampleRate == 16000) {
            samples = Resampler.upsample8kTo16k(pcm, length);
            len = samples.length;
        } else {
            samples = pcm;
            len = length;
        }
        try {
            current.sendAudio(toLittleEndian(samples, len));
            metrics.sttAudioSent(seconds(length));
            utteranceSamples += length;
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
            text = lastInterim;
            lastInterim = null;
            finalPending = false;
            promotedAt = System.currentTimeMillis();
        }
        metrics.sttFinalPromoted();
        log.info("[{}] no final {} ms after the EOU — using the last interim: {}", channelId, finalGraceMs, text);
        clearReplayBuffer();
        if (downstream != null) {
            downstream.onTranscript(text, true, 0f);
        }
    }

    /**
     * Whether a final from the provider is still wanted, or was already delivered from
     * the last interim. A dropped one is logged with both texts: the difference between
     * them says whether the grace is too short.
     */
    private boolean claimFinal(String text) {
        synchronized (finalLock) {
            finalPending = false;
            lastInterim = null;
            if (promotedAt == 0) {
                return true;
            }
            boolean stale = System.currentTimeMillis() - promotedAt < PROMOTED_FINAL_TTL_MS;
            promotedAt = 0;
            if (!stale) {
                return true;
            }
        }
        log.info("[{}] late final dropped, already answered from the interim: {}", channelId, text);
        return false;
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
        providerFailures++;
        try {
            session.close();
        } catch (Exception e) {
            log.debug("[{}] closing the stalled STT session failed: {}", channelId, e.getMessage());
        }
    }

    /**
     * Replace a stream that is no longer carrying audio.
     */
    private synchronized boolean reopen() {
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
        try {
            current.close();
        } catch (Exception e) {
            log.debug("[{}] closing the dead STT session failed: {}", channelId, e.getMessage());
        }
        if (providerFailures >= FAILOVER_AFTER_FAILURES) {
            failOver();
        }
        try {
            utteranceSamples = 0;
            session = provider.startStream(language, alternativeLanguages, this.listener, externalEndpointing, hints);
            lastResponseAt = now;
            streamOpenedAt = now;
            everReady = false;
            droppedFrames = 0;
            log.warn("[{}] STT stream had died — reopened on {}", channelId, provider.name());

            // Replay buffered audio into the fresh stream so speech mid-sentence is not lost!
            if (!replayBuffer.isEmpty()) {
                log.info("[{}] replaying {} ms of speech into reopened STT stream to prevent lost turn",
                        channelId, replayBufferedSamples * 1000 / sourceSampleRate);
                List<short[]> toReplay = new ArrayList<>(replayBuffer);
                for (short[] chunk : toReplay) {
                    sendDirect(chunk, chunk.length);
                }
            }
            return true;
        } catch (Exception e) {
            metrics.sttError();
            log.warn("[{}] STT stream reopen failed: {}", channelId, e.getMessage());
            return false;
        }
    }

    /**
     * Move the call to another vendor after {@link #FAILOVER_AFTER_FAILURES} failures of
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
        SttProvider replacement = selector.findFallback(provider);
        if (replacement == null) {
            // Said once per failure round, not per frame: reopen() is rate-limited by
            // REOPEN_COOLDOWN_MS, and this is the line that explains a call going quiet.
            log.warn("[{}] {} has failed {} times and this build has no other STT provider "
                            + "configured — the call stays on it",
                    channelId, provider.name(), providerFailures);
            providerFailures = 0;
            return;
        }
        log.error("[{}] STT failing over: {} failed {} times, switching to {}",
                channelId, provider.name(), providerFailures, replacement.name());
        metrics.sttFailover(provider.name(), replacement.name());
        provider = replacement;
        targetSampleRate = replacement.sampleRate();
        providerFailures = 0;
    }

    /** Duration of a frame at the source rate — what the provider bills, upsampled or not. */
    private double seconds(int samples) {
        return (double) samples / sourceSampleRate;
    }

    @Override
    public synchronized void close() {
        closed = true;
        clearReplayBuffer();
        try {
            session.close();
        } catch (Exception e) {
            log.warn("Failed to close STT session [{}]: {}", channelId, e.getMessage());
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
