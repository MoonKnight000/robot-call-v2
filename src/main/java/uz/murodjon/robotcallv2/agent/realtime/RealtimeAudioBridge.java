package uz.murodjon.robotcallv2.agent.realtime;

import uz.murodjon.robotcallv2.agent.audio.AudioListener;
import uz.murodjon.robotcallv2.agent.audio.StreamingUpsampler;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleSupplier;

/**
 * Feeds a call's decoded 8 kHz PCM into a realtime engine, resampling to whatever rate
 * that engine listens at — the speech-to-speech counterpart of {@code SttStreamBridge}.
 *
 * <p>Far thinner than that class, and the missing parts are the point. There is no VAD
 * gate, because the engine bills by the minute of an open session rather than by the
 * second of streamed audio, and withholding the silence between words is exactly what
 * breaks its endpointer. There is no end-of-utterance signal, because the engine decides
 * that itself. And there is no reopen-on-failure, because a realtime session carries the
 * whole conversation: a replacement would arrive with no memory of it, answering a caller
 * mid-sentence as though the call had just started.
 *
 * <p>What it does add is an echo gate. The line is full duplex and nothing on the way
 * cancels echo: on a speakerphone, or a trunk with a poor hybrid, the bot's own voice
 * comes back a few hundred milliseconds later, and an engine that hears speech while it is
 * talking stops talking. Caller audio that is much quieter than what the bot has just
 * sent is attenuated before the engine hears it — a real interruption is louder than
 * the echo of the voice it interrupts.
 *
 * <p>Created before the session it feeds, then {@link #arm}ed by {@code
 * RealtimeDialogEngine} once that session is open, and {@link #open}ed once it starts
 * speaking. {@code RtpEndpoint} takes an immutable list of listeners at construction,
 * and the session cannot exist until the endpoint does — it needs somewhere to play the
 * engine's voice. Until both have happened, frames are dropped.
 *
 * <p>Runs on the RTP consumer thread every 20 ms, so it only filters and hands over —
 * {@link RealtimeSession#sendAudio} queues rather than blocking (PROJECT.md §8.3).
 */
public class RealtimeAudioBridge implements AudioListener {

    private static final int TELEPHONE_RATE = 8000;
    /** Low-energy microphone hiss/static below this RMS is noise, not speech. */
    private static final double NOISE_FLOOR_RMS = 70.0;

    private volatile RealtimeSession session;
    /** Null while the engine listens at the telephone rate. */
    private volatile StreamingUpsampler upsampler;
    private volatile boolean open;
    private volatile DoubleSupplier botLevel;
    /** Below this bot level nothing counts as echo ({@code realtime.echo-min-bot-level}). */
    private volatile double minBotLevel;
    private volatile double echoGateRatio;
    private final AtomicLong echoSamples = new AtomicLong();

    /** {@link #arm(RealtimeSession, int, DoubleSupplier, double, double)} without an echo gate. */
    public void arm(RealtimeSession session, int engineRate) {
        arm(session, engineRate, null, 0, 0);
    }

    /**
     * Point this bridge at the now-open session.
     *
     * @param engineRate    {@link RealtimeProvider#inputSampleRate()} of the engine on the
     *                      other end. Rejected here rather than tolerated if it is a rate
     *                      this class cannot produce: audio sent at the wrong rate is
     *                      accepted on the wire and comes back as gibberish, so failing to
     *                      set the call up is the better failure
     * @param botLevel      the bot's recent outgoing level ({@code RtpEndpoint
     *                      #recentOutboundLevel}); null disables the echo gate
     * @param echoGateRatio share of that level under which caller audio counts as echo;
     *                      0 disables the gate
     * @param minBotLevel   bot level under which nothing counts as echo — it is not talking
     */
    public void arm(RealtimeSession session, int engineRate, DoubleSupplier botLevel, double echoGateRatio, double minBotLevel) {
        this.minBotLevel = minBotLevel;
        this.upsampler = StreamingUpsampler.from8kTo(engineRate);
        this.botLevel = botLevel;
        this.echoGateRatio = echoGateRatio;
        this.session = session;
    }

    /**
     * Start forwarding the caller, which an armed bridge does not do on its own.
     *
     * <p>The gap between the two matters at exactly one moment: the engine is connected
     * but has not said anything yet, and the caller is saying "alo?" into it. Forwarded,
     * that hello is a second reason for the engine to talk — on top of the opening turn
     * the dialog engine sends it — and it answers both, greeting the caller twice. So the
     * line opens on the engine's first word instead, which costs nothing: until then it
     * had nothing to interrupt. Barge-in from that point on is unaffected.
     *
     * <p>Idempotent — called on every frame the engine speaks.
     */
    public void open() {
        this.open = true;
    }

    /** Caller audio the echo gate attenuated so far, in seconds. */
    public double echoSuppressedSeconds() {
        return echoSamples.get() / (double) TELEPHONE_RATE;
    }

    @Override
    public void onAudio(short[] pcm, int length) {
        RealtimeSession current = session;
        if (current == null || !open || length <= 0) {
            return;
        }
        // A copy: the endpoint hands every listener the same buffer and reuses it for the
        // next packet, so neither the gate nor the upsampler may keep or change it.
        short[] frame = Arrays.copyOf(pcm, length);
        double sumSq = 0;
        for (short sample : frame) {
            sumSq += (double) sample * sample;
        }
        double rms = Math.sqrt(sumSq / frame.length);

        if (isEcho(rms)) {
            // Silence echo completely so speech-to-speech models never hear their own voice.
            Arrays.fill(frame, (short) 0);
            echoSamples.addAndGet(length);
        } else if (rms < NOISE_FLOOR_RMS) {
            // Mute low-energy microphone hiss/static during silence to prevent ASR hallucinations.
            Arrays.fill(frame, (short) 0);
        }
        StreamingUpsampler up = upsampler;
        short[] out = up == null ? frame : up.push(frame, frame.length);
        if (out.length > 0) {
            current.sendAudio(out);
        }
    }

    private boolean isEcho(double rms) {
        DoubleSupplier level = botLevel;
        double ratio = echoGateRatio;
        if (level == null || ratio <= 0) {
            return false;
        }
        double bot = level.getAsDouble();
        if (bot < minBotLevel) {
            return false;
        }
        return rms < bot * ratio;
    }
}
