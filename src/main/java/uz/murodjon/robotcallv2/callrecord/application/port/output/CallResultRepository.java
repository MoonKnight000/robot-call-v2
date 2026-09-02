package uz.murodjon.robotcallv2.callrecord.application.port.output;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface CallResultRepository {
    void insertIgnoringConflict(long callId, String summary, String reasonCode, LocalDate promisedDate,
                                BigDecimal promisedAmount, String sentiment, boolean needsFollowUp,
                                String followUpNote, boolean escalated, Long crmNoteId, String outcome);
}
