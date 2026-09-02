package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.callrecord.application.port.output.CallTranscriptRepository;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallTranscriptEntity;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallAttemptJpaRepository;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallTranscriptJpaRepository;

import java.util.List;

@Component
public class CallTranscriptRepositoryAdapter implements CallTranscriptRepository {

    private final CallTranscriptJpaRepository jpa;
    private final CallAttemptJpaRepository callAttempts;

    public CallTranscriptRepositoryAdapter(CallTranscriptJpaRepository jpa, CallAttemptJpaRepository callAttempts) {
        this.jpa = jpa;
        this.callAttempts = callAttempts;
    }

    @Override
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

    @Override
    public List<String> transcriptLines(long callId) {
        return jpa.findByCall_IdOrderBySeq(callId).stream()
                .map(e -> e.getRole() + ": " + e.getText())
                .toList();
    }
}
