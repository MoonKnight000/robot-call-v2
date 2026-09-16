package uz.murodjon.robotcallv2.callrecord.application.port.input;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.callrecord.domain.entity.AnsweredCall;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallAttempt;
import uz.murodjon.robotcallv2.callrecord.domain.entity.ContactHistory;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.util.List;

/**
 * A call's {@code call_attempt} row, from the moment it is dialled until it is closed out, and
 * the questions the rest of the platform asks of it.
 *
 * <p>The writes are fail-open: they run on ARI/STT callback threads, and a database error is
 * logged rather than allowed to end the phone call in progress.
 */
public interface CallAttemptUseCase {

    /**
     * Opens a call_attempt; returns its id, or 0 on failure.
     *
     * @param targetId the campaign target, or {@link CallAttempt#NO_TARGET} for a call no
     *                 campaign placed (manual, test, widget, operator, inbound)
     */
    long startAttempt(long companyId, long targetId, String channelId, String phone,
                      String language, Long inboundRouteId, Long variantId);

    /** Records an attempt that never became a call; returns its id, or 0 on failure. */
    long recordUnplacedAttempt(long companyId, long targetId, String phone, String language,
                               Disposition disposition, String reason);

    /** Which agent, and which published version of it, ran the call. */
    void recordAgent(long callId, AiAgent agent);

    void markAnswered(long callId);

    void recordError(long callId, String message);

    void recordHangupCause(String channelId, String cause);

    /** Closes out the attempt for a call nobody picked up; a no-op once it was answered. */
    void finishUnansweredAttempt(String channelId, Disposition disposition);

    void finishAttempt(long callId, Disposition disposition, Long recordingFileId, int durationSec);

    /** The attempt opened for {@code channelId} when the call was dialled, or 0. */
    long findAttemptIdByChannel(String channelId);

    /** The company that owns the attempt; the default company only when there is no attempt to ask. */
    long findCompanyIdByAttemptId(long callAttemptId);

    /** The number the attempt talked to; {@code null} when there is no such attempt. */
    String findPhoneByAttemptId(long callAttemptId);

    /** The inbound route the attempt came in on; {@code null} for an outbound call or on failure. */
    Long findInboundRouteIdByAttemptId(long callAttemptId);

    /** Answered calls this company made to that number between two instants, newest first. */
    List<AnsweredCall> findAnsweredCalls(long companyId, String phone, Instant from, Instant to);

    /** What the company's contact policy needs to know about one number; empty when unreadable. */
    ContactHistory findContactHistory(long companyId, String phone, Instant since, int attemptLimit);

    /** Who last spoke to this caller as an operator — the sticky routing target. */
    Long findLastOperator(long companyId, String phone, Instant since);

    /** The call joined a queue; the first queue is the one SLA counts from. */
    void markEnqueued(long callId, long queueId);

    /** An operator answered: who, and how long the caller had waited. */
    void markOperatorAnswered(long callId, long userId, int waitSeconds);

    /** The caller gave up waiting. */
    void markAbandoned(long callId, int waitSeconds);

    /** The 1-5 rating the caller gave after the operator hung up. */
    void recordSurveyScore(long callId, int score);

    /** Attempts of every company that ended since {@code since} — the platform-wide success-rate alert. */
    long countEndedSince(Instant since);

    /** The same, only those that ended with {@code disposition}. */
    long countEndedSinceWithDisposition(Instant since, Disposition disposition);
}
