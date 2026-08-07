package uz.murodjon.uysotvoice.callrecord.repository;

import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.callrecord.entity.CallTranscriptEntity;

import java.util.List;

/** JPA-backed DAO for {@code call_transcript} (PROJECT.md §6, Stage 9). */
@Repository
public class CallTranscriptRepository {

    private final CallTranscriptJpaRepository jpa;
    private final CallAttemptJpaRepository callAttempts;

    public CallTranscriptRepository(CallTranscriptJpaRepository jpa, CallAttemptJpaRepository callAttempts) {
        this.jpa = jpa;
        this.callAttempts = callAttempts;
    }

    /** Insert one transcript line. */
    public void save(long callId, int seq, String role, String text, String dialogState, int tsOffsetMs,
                     Float confidence) {
        CallTranscriptEntity entity = new CallTranscriptEntity();
        entity.setCall(callAttempts.getReferenceById(callId));
        entity.setSeq(seq);
        entity.setRole(role);
        entity.setText(text);
        entity.setDialogState(dialogState);
        entity.setTsOffsetMs(Math.max(0, tsOffsetMs));
        entity.setSttConfidence(confidence);
        jpa.save(entity);
    }

    /** {@code "ROLE: text"} lines in order — what the summary LLM prompt is built from. */
    public List<String> transcriptLines(long callId) {
        return jpa.findByCall_IdOrderBySeq(callId).stream()
                .map(e -> e.getRole() + ": " + e.getText())
                .toList();
    }
}
