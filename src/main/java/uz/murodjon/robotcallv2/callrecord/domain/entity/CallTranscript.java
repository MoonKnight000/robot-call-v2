package uz.murodjon.robotcallv2.callrecord.domain.entity;

/** Pure domain record for a single utterance line in a call transcript. */
public record CallTranscript(
        long id,
        long callId,
        int seq,
        String role,
        String text,
        String dialogState,
        int tsOffsetMs,
        Float sttConfidence
) {
}
