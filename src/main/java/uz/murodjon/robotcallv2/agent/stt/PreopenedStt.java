package uz.murodjon.robotcallv2.agent.stt;

/**
 * A recognition stream opened at ring time ({@link SttPrewarmRegistry}), waiting for the
 * bridge of the call that answers to adopt it.
 *
 * @param session     the open stream
 * @param params      what it was opened with
 * @param expiresAtMs when it is closed if nobody has taken it (epoch millis)
 * @param forwarder   the stream's listener; {@link SttStreamBridge} binds itself to it on adoption
 */
public record PreopenedStt(SttSession session, SttOpenParams params, long expiresAtMs,
                           ForwardingTranscriptListener forwarder) {
}
