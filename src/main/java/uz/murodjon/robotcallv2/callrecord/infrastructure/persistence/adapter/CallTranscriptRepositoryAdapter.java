package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.callrecord.application.port.output.CallTranscriptRepository;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallTranscript;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallTranscriptEntity;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallAttemptJpaRepository;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallTranscriptJpaRepository;

import java.time.Instant;
import java.util.List;

@Component
public class CallTranscriptRepositoryAdapter implements CallTranscriptRepository {

    private final CallTranscriptJpaRepository callTranscriptJpaRepository;
    private final CallAttemptJpaRepository callAttemptJpaRepository;

    public CallTranscriptRepositoryAdapter(CallTranscriptJpaRepository callTranscriptJpaRepository,
                                           CallAttemptJpaRepository callAttemptJpaRepository) {
        this.callTranscriptJpaRepository = callTranscriptJpaRepository;
        this.callAttemptJpaRepository = callAttemptJpaRepository;
    }

    @Override
    public void save(CallTranscript transcript) {
        CallTranscriptEntity entity = new CallTranscriptEntity();
        entity.setCall(callAttemptJpaRepository.getReferenceById(transcript.callId()));
        entity.setSeq(transcript.seq());
        entity.setRole(transcript.role());
        entity.setText(transcript.text());
        entity.setDialogState(transcript.dialogState());
        entity.setTsOffsetMs(Math.max(0, transcript.tsOffsetMs()));
        entity.setSttConfidence(transcript.sttConfidence());
        callTranscriptJpaRepository.save(entity);
    }

    @Override
    public List<String> findTranscriptLines(long callId) {
        return callTranscriptJpaRepository.findByCall_IdOrderBySeq(callId).stream()
                .map(entity -> entity.getRole() + ": " + entity.getText())
                .toList();
    }

    @Override
    public int purgeEndedBefore(Instant cutoff) {
        return callTranscriptJpaRepository.purgeForAttemptsEndedBefore(cutoff);
    }
}
