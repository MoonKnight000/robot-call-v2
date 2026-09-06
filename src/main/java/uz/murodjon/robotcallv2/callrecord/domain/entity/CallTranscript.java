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

    /** A line on its way to storage; the id is the database's to assign. */
    public static CallTranscript line(long callId, int seq, String role, String text, String dialogState,
                                      int tsOffsetMs, Float sttConfidence) {
        return new CallTranscript(0, callId, seq, role, text, dialogState, tsOffsetMs, sttConfidence);
    }
}
