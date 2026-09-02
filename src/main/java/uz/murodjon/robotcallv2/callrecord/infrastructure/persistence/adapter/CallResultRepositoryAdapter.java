package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.callrecord.application.port.output.CallResultRepository;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallResultJpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class CallResultRepositoryAdapter implements CallResultRepository {

    private final CallResultJpaRepository jpa;

    public CallResultRepositoryAdapter(CallResultJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void insertIgnoringConflict(long callId, String summary, String reasonCode, LocalDate promisedDate,
                                       BigDecimal promisedAmount, String sentiment, boolean needsFollowUp,
                                       String followUpNote, boolean escalated, Long crmNoteId, String outcome) {
        jpa.insertIgnoringConflict(callId, summary, reasonCode, promisedDate, promisedAmount, sentiment,
                needsFollowUp, followUpNote, escalated, crmNoteId, outcome);
    }
}
