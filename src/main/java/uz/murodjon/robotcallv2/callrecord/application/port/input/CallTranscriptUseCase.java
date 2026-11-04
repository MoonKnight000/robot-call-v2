package uz.murodjon.robotcallv2.callrecord.application.port.input;

/** A call's transcript, line by line as it is spoken, and as a whole once it is over. */
public interface CallTranscriptUseCase {

    /** Inserts one transcript line; its sequence number is assigned per call. */
    void addTranscript(long callId, String role, String text, String dialogState, int tsOffsetMs, Float confidence);

    /**
     * Replaces the text of the call's last {@code AGENT} line with what the caller heard of it,
     * or removes that line when {@code heardText} is {@code null} or blank.
     */
    void correctLastAgentLine(long callId, String heardText);

    /** The full transcript as {@code ROLE: text} lines, in order; empty when there is none. */
    String findTranscriptText(long callId);
}
