package uz.murodjon.robotcallv2.agent.realtime;

import uz.murodjon.robotcallv2.agent.audio.AudioListener;
import uz.murodjon.robotcallv2.agent.audio.Resampler;

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
 * <p>Created before the session it feeds, then {@link #arm}ed by {@code
 * RealtimeDialogEngine} once that session is open, and {@link #open}ed once it starts
 * speaking. {@code RtpEndpoint} takes an immutable list of listeners at construction,
 * and the session cannot exist until the endpoint does — it needs somewhere to play the
 * engine's voice. Until both have happened, frames are dropped.
 *
 * <p>Runs on the RTP consumer thread every 20 ms, so it only resamples and hands over —
 * {@link RealtimeSession#sendAudio} queues rather than blocking (PROJECT.md §8.3).
 */
public class RealtimeAudioBridge implements AudioListener {

    private static final int TELEPHONE_RATE = 8000;

    private volatile RealtimeSession session;
    private volatile int engineRate;
    private volatile boolean open;

    /**
     * Point this bridge at the now-open session.
     *
     * @param engineRate {@link RealtimeProvider#inputSampleRate()} of the engine on the
     *                   other end. Rejected here rather than tolerated if it is a rate
     *                   this class cannot produce: audio sent at the wrong rate is
     *                   accepted on the wire and comes back as gibberish, so failing to
     *                   set the call up is the better failure
     */
    public void arm(RealtimeSession session, int engineRate) {
        if (engineRate != TELEPHONE_RATE && engineRate != 16000) {
            throw new IllegalArgumentException("no resampler from 8 kHz to " + engineRate + " Hz");
        }
        this.engineRate = engineRate;
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

    @Override
    public void onAudio(short[] pcm, int length) {
        RealtimeSession current = session;
        if (current == null || !open || length <= 0) {
            return;
        }
        if (engineRate == TELEPHONE_RATE) {
            short[] frame = new short[length];
            System.arraycopy(pcm, 0, frame, 0, length);
            current.sendAudio(frame);
            return;
        }
        current.sendAudio(Resampler.upsample8kTo16k(pcm, length));
    }
}
