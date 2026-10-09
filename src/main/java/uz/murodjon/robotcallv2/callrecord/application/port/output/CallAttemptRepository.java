package uz.murodjon.robotcallv2.callrecord.application.port.output;

import uz.murodjon.robotcallv2.callrecord.domain.entity.AnsweredCall;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallAttempt;
import uz.murodjon.robotcallv2.callrecord.domain.entity.ContactHistory;
import uz.murodjon.robotcallv2.callrecord.domain.entity.PhoneCallHistory;
import uz.murodjon.robotcallv2.callrecord.domain.entity.ConversationRetention;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CallAttemptRepository {
    Long findCompanyIdById(long id);
    CallAttempt findByCompanyIdAndId(long companyId, long id);
    Long findLatestIdByTargetIdSince(long companyId, long targetId, Instant since);
    Long findTargetIdById(long id);
    String findPhoneById(long id);

    /** {@code true} when the call came in, {@code null} when there is no such attempt. */
    Boolean findInboundById(long id);

    /** The inbound route the call came in on, or null for an outbound or unknown attempt. */
    Long findInboundRouteIdById(long id);

    /**
     * Every answered call this company made to that number between two instants.
     *
     * <p>Reduced to { AnsweredCall} rather than whole attempts: the caller is
     * deciding which call earned a conversion, and that is a comparison of answer times.
     */
    List<AnsweredCall> findAnsweredByPhone(long companyId, String phone, Instant from, Instant to);

    /**
     * This company's contact with one number, as its contact policy reads it: the placed
     * outbound attempts since {@code since} (newest first, at most {@code attemptLimit}),
     * the last real conversation, and the latest promised payment date.
     */
    ContactHistory findContactHistory(long companyId, String phone, Instant since, int attemptLimit);

    /**
     * Outbound calls to each of {@code phones} that reached the trunk since {@code since}, with
     * the hours (in {@code zone}) the reached ones were answered in. Numbers with none are left out.
     */
    Map<String, PhoneCallHistory> findPhoneCallHistories(long companyId, Collection<String> phones, Instant since,
                                                         ZoneId zone);
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

    /** The call joined {@code queueId} now. */
    void markEnqueued(long callId, long queueId, Instant at);

    /** An operator picked the call up after {@code waitSeconds} in the queue. */
    void markOperatorAnswered(long callId, long userId, Instant at, int waitSeconds);

    /** The caller hung up while still waiting. */
    void markAbandoned(long callId, int waitSeconds);

    /** A predictive call no operator took after {@code waitSeconds}: the campaign's AI agent talks instead. */
    void markAiOverflow(long callId, int waitSeconds);

    void recordSurveyScore(long callId, int score);

    /** The operator who last answered a call from {@code phone} since {@code since}; null when none did. */
    Long findLastOperatorUserId(long companyId, String phone, Instant since);
    void finishAttempt(long callId, Instant endedAt, int durationSec, Disposition disposition, Long recordingFileId);

    /** The outcome alone — ended, duration and recording stay as they are. */
    void updateDisposition(long callId, Disposition disposition);

    /** The agent that ran the call and its published version, null for a draft (NEXT-PLAN A.1). */
    void recordAgent(long callId, Long agentId, Integer agentVersion);

    /** Up to {@code batchSize} stored recordings of calls whose conversation is past its retention. */
    List<Long> findExpiredRecordingFileIds(ConversationRetention retention, int batchSize);
}
