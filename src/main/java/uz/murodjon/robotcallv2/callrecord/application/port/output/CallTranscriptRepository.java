package uz.murodjon.robotcallv2.callrecord.application.port.output;

import uz.murodjon.robotcallv2.callrecord.domain.entity.CallTranscript;
import uz.murodjon.robotcallv2.callrecord.domain.entity.ConversationRetention;

import java.util.List;

public interface CallTranscriptRepository {

    void save(CallTranscript transcript);

    List<String> findTranscriptLines(long callId);

    /** Replaces the text of the call's last line said by {@code role}; returns the row count. */
    int updateLastLineText(long callId, String role, String text);

    /** Removes the call's last line said by {@code role}; returns the row count. */
    int deleteLastLine(long callId, String role);

    /** Drops up to {@code batchSize} lines of conversations past their retention; returns the row count. */
    int deleteExpired(ConversationRetention retention, int batchSize);
}
