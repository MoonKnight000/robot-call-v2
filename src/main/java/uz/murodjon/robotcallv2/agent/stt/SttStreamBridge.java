package uz.murodjon.robotcallv2.agent.stt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uz.murodjon.robotcallv2.agent.audio.AudioListener;
import uz.murodjon.robotcallv2.agent.audio.Resampler;
import uz.murodjon.robotcallv2.agent.audio.SpeechGate;
import uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics;

import java.io.Closeable;
import java.util.List;

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
 * <p>With {@link EndpointingProperties} enabled it also decides <em>when the caller has
 * finished</em>: the gate shutting is the end of the utterance, and the provider is told
 * so rather than waiting for its own detector. A safety net forces the signal on an
 * utterance that never seems to end, because nothing else would.
 *
 * <p>Also owns the stream's lifetime, not just its contents: a recognizer torn down
 * mid-call is replaced (see {@link #reopen()}) rather than left to swallow the rest of
 * the call's audio. A stream that fails without saying so is caught the same way — one
 * that cannot flush has its frames dropped instead of queued, and one that answers
 * nothing at all is replaced (see {@link #dropStalledStream()}).
 */
public class SttStreamBridge implements AudioListener, Closeable {

    private static final Logger log = LoggerFactory.getLogger(SttStreamBridge.class);

    /**
     * How long to wait before opening another stream after a failed attempt. Audio
     * arrives every 20 ms, so without a floor a provider that is simply down (a rejected
     * API key) would be asked for fifty new streams a second.
     */
    private static final long REOPEN_COOLDOWN_MS = 1000;

    private final String channelId;
    private final int targetSampleRate;
    private final int sourceSampleRate;
    private final SpeechGate gate;
    private final VoiceMetrics metrics;
    private final SttProvider provider;
    private final String language;
    /** Other languages this caller may answer in ({@code voice-agent.stt.detect-languages}). */
    private final List<String> alternativeLanguages;
    private final TranscriptListener listener;
    /** Whether this call ends its own utterances rather than letting the provider do it. */
    private final boolean externalEndpointing;
    private final long maxUtteranceSamples;
    /** How long a stream may take audio without answering before it counts as stalled; 0 = never. */
    private final long responseTimeoutMs;

    /** Audio sent since the current utterance began — 0 while the caller is silent. */
    private long utteranceSamples;

    private volatile SttSession session;
    /** Set by {@link #close()} — a call that has ended must not reopen anything. */
    private volatile boolean closed;
    private long lastReopenAttemptAt;
    /** When the provider last said anything at all — written from its own thread. */
    private volatile long lastResponseAt;
    /** Frames thrown away since the transport last accepted one. */
    private long droppedFrames;

    /** Whether the previous frame was sent — only used to log gate transitions. */
    private boolean streaming = true;

    /**
     * @param endpointing       when enabled <em>and</em> a gate is installed, this call decides
     *                          when the caller has finished and tells the provider. Without a
     *                          gate there is nothing to decide it with, so the provider's own
     *                          detector stays in charge whatever the setting says
     * @param responseTimeoutMs how long a stream may swallow audio without answering before
     *                          it is replaced ({@code voice-agent.stt.response-timeout-ms})
     */
    public SttStreamBridge(SttProvider provider, int targetSampleRate, int sourceSampleRate, String channelId,
                           String language, List<String> alternativeLanguages, TranscriptListener listener,
                           SpeechGate gate, VoiceMetrics metrics, EndpointingProperties endpointing,
                           int responseTimeoutMs) {
        this.channelId = channelId;
        this.targetSampleRate = targetSampleRate;
        this.sourceSampleRate = sourceSampleRate;
        this.gate = gate;
        this.metrics = metrics;
        this.provider = provider;
        this.language = language;
        this.alternativeLanguages = alternativeLanguages == null ? List.of() : List.copyOf(alternativeLanguages);
        this.listener = (text, isFinal, confidence) -> {
            lastResponseAt = System.currentTimeMillis();
            if (closed) {
                // A stream that was holding audio back delivers all of it the moment it
                // finally flushes, which can be after the hangup. By then the dialog is
                // over and the call record is written; forwarding these would answer a
                // question nobody is waiting for and file transcript rows at an offset
                // past the end of the call.
                log.warn("[{}] transcript arrived after the call ended, ignored: {}", channelId, text);
                return;
            }
            if (!isFinal && gate != null) {
                gate.onInterimTranscript(text);
            }
            if (listener != null) {
                listener.onTranscript(text, isFinal, confidence);
            }
        };
        this.externalEndpointing = endpointing != null && endpointing.enabled() && gate != null;
        this.maxUtteranceSamples = externalEndpointing
                ? (long) Math.max(1000, endpointing.maxUtteranceMs()) * sourceSampleRate / 1000
                : Long.MAX_VALUE;
        this.responseTimeoutMs = Math.max(0, responseTimeoutMs);
        this.lastResponseAt = System.currentTimeMillis();
        this.session = provider.startStream(language, this.alternativeLanguages, this.listener, externalEndpointing);
    }

    @Override
    public void onAudio(short[] pcm, int length) {
        // The VAD listener runs before this one on the same thread, so the gate has
        // already scored the frame in hand — an utterance opens the gate on its own
        // first frame rather than the next one.
        if (gate != null && !gate.isOpen()) {
            gate.buffer(pcm, length);
            metrics.sttAudioSkipped(seconds(length));
            if (streaming) {
                streaming = false;
                // The gate shutting is the end of the utterance: speech stopped and its
                // hangover has run out. With external endpointing the recognizer is
                // waiting to be told exactly this before it will finalize.
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
                // The run-up to the first detected window: without it the provider
                // hears an utterance that starts mid-word.
                send(preRoll, preRoll.length);
            }
        }
        send(pcm, length);
    }

    private void send(short[] pcm, int length) {
        SttSession current = session;
        if (!current.isAlive() && !reopen()) {
            // Still no stream — drop the frame. Live audio cannot be queued for later:
            // by the time a stream exists this sentence is long over, and replaying it
            // would only hand the recognizer speech from the wrong part of the call.
            return;
        }
        if (!session.isReady()) {
            // The same rule, one layer down: the transport is alive but cannot flush, and
            // it would queue this frame rather than refuse it. A call's worth of audio
            // buffered that way arrives at the recognizer after the hangup — meanwhile the
            // bot hears nothing, the silence watchdog fires and it hangs up on someone who
            // is talking. Dropping keeps the loss where it happens, and visible.
            metrics.sttAudioSkipped(seconds(length));
            if (droppedFrames++ == 0) {
                log.warn("[{}] STT transport is not accepting audio — dropping frames "
                        + "(the caller cannot be heard until it recovers)", channelId);
            }
            return;
        }
        if (droppedFrames > 0) {
            log.warn("[{}] STT transport accepting audio again — {} frame(s) dropped", channelId, droppedFrames);
            droppedFrames = 0;
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
            session.sendAudio(toLittleEndian(samples, len));
            metrics.sttAudioSent(seconds(length));
            utteranceSamples += length;
            if (utteranceSamples >= maxUtteranceSamples) {
                // Nothing but us can end an utterance on this stream, so a gate that
                // never shuts (VAD bypassed after an inference error, or a line with
                // constant noise) would leave the turn hanging for the rest of the call.
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
     * Sends nothing when no audio has gone out since the last one: the gate shuts once
     * at the start of every call, before anybody has said a word.
     */
    private void endUtterance() {
        boolean spoke = utteranceSamples > 0;
        utteranceSamples = 0;
        if (!externalEndpointing || !spoke) {
            return;
        }
        SttSession current = session;
        if (!current.isAlive()) {
            return; // a reopened stream starts its own utterance anyway
        }
        try {
            current.endUtterance();
            metrics.sttUtteranceEnded();
            metrics.sttEndpointingHangover(gate.hangoverMs());
        } catch (Exception e) {
            log.warn("[{}] end-of-utterance signal failed: {}", channelId, e.getMessage());
        }
    }

    /**
     * Give up on a stream that takes audio but never answers.
     *
     * <p>{@link SttSession#isReady()} covers a transport that admits it cannot write.
     * A stream can also accept every frame and simply go quiet — the far side stopped
     * reading, or the socket is half-open and the kernel is still swallowing bytes. The
     * session is alive by every measure available to us, so nothing else notices; from
     * the call's point of view the caller has gone silent.
     *
     * <p>Only the response side is timed, never speech: on a call with no VAD gate the
     * caller's silence is streamed too, so a recognizer with nothing to say is normal.
     * The timeout therefore has to outlast the longest stretch a caller can reasonably
     * stay quiet — and a stream replaced during real silence costs nothing, because no
     * speech was in flight to lose.
     *
     * <p>Marks the session dead rather than reopening here; the next frame goes through
     * {@link #reopen()} like any other dead stream.
     */
    private void dropStalledStream() {
        if (responseTimeoutMs == 0 || System.currentTimeMillis() - lastResponseAt < responseTimeoutMs) {
            return;
        }
        lastResponseAt = System.currentTimeMillis();
        log.warn("[{}] {} took audio for {} ms without a single transcript — dropping the stream",
                channelId, provider.name(), responseTimeoutMs);
        metrics.sttError();
        try {
            session.close();
        } catch (Exception e) {
            log.debug("[{}] closing the stalled STT session failed: {}", channelId, e.getMessage());
        }
    }

    /**
     * Replace a stream that is no longer carrying audio.
     *
     * <p>A recognizer can be torn down mid-call — the provider's own session limit, a
     * dropped connection, a GOAWAY. Nothing else about the call notices: the bot just
     * stops hearing, and what the operator sees is a caller who apparently went silent.
     * Reopening keeps the rest of the call alive; only the speech spoken during the gap
     * is lost.
     *
     * <p>Synchronized because audio frames and the close on hangup arrive on different
     * threads, and two of them racing here would leave an orphaned stream billing away.
     *
     * @return whether a live session is now in place
     */
    private synchronized boolean reopen() {
        SttSession current = session;
        if (current.isAlive()) {
            return true; // another frame won the race and already reopened it
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
        try {
            utteranceSamples = 0; // whatever was mid-utterance died with the old stream
            session = provider.startStream(language, alternativeLanguages, this.listener, externalEndpointing);
            lastResponseAt = now; // a fresh stream has not had a chance to answer yet
            droppedFrames = 0;
            log.warn("[{}] STT stream had died — reopened", channelId);
            return true;
        } catch (Exception e) {
            metrics.sttError();
            log.warn("[{}] STT stream reopen failed: {}", channelId, e.getMessage());
            return false;
        }
    }

    /** Duration of a frame at the source rate — what the provider bills, upsampled or not. */
    private double seconds(int samples) {
        return (double) samples / sourceSampleRate;
    }

    @Override
    public synchronized void close() {
        closed = true;
        try {
            session.close();
        } catch (Exception e) {
            log.warn("STT close failed [{}]: {}", channelId, e.getMessage());
        }
    }

    private static byte[] toLittleEndian(short[] pcm, int len) {
        byte[] out = new byte[len * 2];
        for (int i = 0; i < len; i++) {
            short s = pcm[i];
            out[i * 2] = (byte) (s & 0xFF);
            out[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }
        return out;
    }
}
