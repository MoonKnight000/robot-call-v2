package uz.murodjon.robotcallv2.callrecord.application.port.output;

import uz.murodjon.robotcallv2.callrecord.domain.entity.CallAttempt;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.util.Optional;

public interface CallAttemptRepository {
    Long findCompanyIdById(long id);
    Long findTargetIdById(long id);
    String findPhoneById(long id);
    Optional<Long> findTargetIdByPhone(String phone);
    long create(CallAttempt attempt);
    Optional<Long> findIdByChannel(String channelId);
    long countEndedSince(Instant since);
    long countEndedSinceWithDisposition(Instant since, Disposition disposition);
    void markAnswered(long callId, Instant answeredAt);
    void finishUnanswered(String channelId, Instant endedAt, Disposition disposition);
    void recordError(long callId, String message);
    void updateLanguage(long callId, String language);
    void recordHangupCause(String channelId, String cause);
    void assignOperator(long callId, long userId);
    void finishAttempt(long callId, Instant endedAt, int durationSec, Disposition disposition, Long recordingFileId);
}
