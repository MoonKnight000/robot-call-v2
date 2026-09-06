package uz.murodjon.robotcallv2.callrecord.application.port.output;

import uz.murodjon.robotcallv2.callrecord.domain.entity.CallTranscript;

import java.time.Instant;
import java.util.List;

public interface CallTranscriptRepository {

    void save(CallTranscript transcript);

    List<String> findTranscriptLines(long callId);

    /** Drops the verbatim speech of every call that ended before {@code cutoff}; returns the row count. */
    int purgeEndedBefore(Instant cutoff);
}
