package uz.murodjon.robotcallv2.callrecord.application.port.output;

import java.util.List;

public interface CallTranscriptRepository {
    void save(long callId, int seq, String role, String text, String dialogState, int tsOffsetMs, Float confidence);
    List<String> transcriptLines(long callId);
}
