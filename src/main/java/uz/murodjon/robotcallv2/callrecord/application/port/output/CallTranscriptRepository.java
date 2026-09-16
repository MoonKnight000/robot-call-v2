package uz.murodjon.robotcallv2.callrecord.application.port.output;

import uz.murodjon.robotcallv2.callrecord.domain.entity.CallTranscript;
import uz.murodjon.robotcallv2.callrecord.domain.entity.ConversationRetention;

import java.util.List;

public interface CallTranscriptRepository {

    void save(CallTranscript transcript);

    List<String> findTranscriptLines(long callId);

    /** Drops up to {@code batchSize} lines of conversations past their retention; returns the row count. */
    int deleteExpired(ConversationRetention retention, int batchSize);
}
