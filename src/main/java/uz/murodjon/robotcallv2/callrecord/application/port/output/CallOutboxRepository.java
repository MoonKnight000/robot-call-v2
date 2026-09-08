package uz.murodjon.robotcallv2.callrecord.application.port.output;

import uz.murodjon.robotcallv2.callrecord.application.dto.PendingNote;
import uz.murodjon.robotcallv2.callrecord.application.dto.PendingSummary;

import java.util.List;

public interface CallOutboxRepository {
    List<PendingNote> notesAwaitingCrm(int maxAttempts, int limit);
    List<PendingSummary> attemptsAwaitingSummary(int maxAttempts, int limit);
    void markCrmPosted(long callId, String noteId);
    void markCrmFailed(long callId, String error);
    void countSummaryAttempt(long callId);
}
