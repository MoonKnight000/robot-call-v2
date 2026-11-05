package uz.murodjon.robotcallv2.agent.stt;

/**
 * The listener of a stream opened before the call it is for has a bridge: transcripts go
 * nowhere until {@link #bind} names the receiver, and nothing is expected before then (no
 * audio has been sent), so a stray one is dropped rather than held.
 */
public final class ForwardingTranscriptListener implements TranscriptListener {

    private volatile TranscriptListener target;

    public void bind(TranscriptListener receiver) {
        this.target = receiver;
    }

    @Override
    public void onTranscript(String text, boolean isFinal, float confidence) {
        TranscriptListener receiver = target;
        if (receiver != null) {
            receiver.onTranscript(text, isFinal, confidence);
        }
    }
}
