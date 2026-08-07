package uz.murodjon.uysotvoice.agent.stt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uz.murodjon.uysotvoice.agent.audio.AudioListener;
import uz.murodjon.uysotvoice.agent.audio.Resampler;
import uz.murodjon.uysotvoice.agent.audio.SpeechGate;
import uz.murodjon.uysotvoice.agent.metrics.VoiceMetrics;

import java.io.Closeable;

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
 * the call's audio.
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
    private final TranscriptListener listener;
    /** Whether this call ends its own utterances rather than letting the provider do it. */
    private final boolean externalEndpointing;
    private final long maxUtteranceSamples;

    /** Audio sent since the current utterance began — 0 while the caller is silent. */
    private long utteranceSamples;

    private volatile SttSession session;
    /** Set by {@link #close()} — a call that has ended must not reopen anything. */
    private volatile boolean closed;
    private long lastReopenAttemptAt;

    /** Whether the previous frame was sent — only used to log gate transitions. */
    private boolean streaming = true;

    /**
     * @param endpointing when enabled <em>and</em> a gate is installed, this call decides
     *                    when the caller has finished and tells the provider. Without a
     *                    gate there is nothing to decide it with, so the provider's own
     *                    detector stays in charge whatever the setting says
     */
    public SttStreamBridge(SttProvider provider, int targetSampleRate, int sourceSampleRate, String channelId,
                           String language, TranscriptListener listener, SpeechGate gate, VoiceMetrics metrics,
                           EndpointingProperties endpointing) {
        this.channelId = channelId;
        this.targetSampleRate = targetSampleRate;
        this.sourceSampleRate = sourceSampleRate;
        this.gate = gate;
        this.metrics = metrics;
        this.provider = provider;
        this.language = language;
        this.listener = listener;
        this.externalEndpointing = endpointing != null && endpointing.enabled() && gate != null;
        this.maxUtteranceSamples = externalEndpointing
                ? (long) Math.max(1000, endpointing.maxUtteranceMs()) * sourceSampleRate / 1000
                : Long.MAX_VALUE;
        this.session = provider.startStream(language, listener, externalEndpointing);
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
        } catch (Exception e) {
            log.warn("[{}] end-of-utterance signal failed: {}", channelId, e.getMessage());
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
            session = provider.startStream(language, listener, externalEndpointing);
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
