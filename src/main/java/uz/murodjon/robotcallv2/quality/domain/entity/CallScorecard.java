package uz.murodjon.robotcallv2.quality.domain.entity;

import uz.murodjon.robotcallv2.quality.domain.enums.ScorecardReviewStatus;
import uz.murodjon.robotcallv2.shared.dialog.Sentiment;

import java.time.Instant;
import java.util.List;

/**
 * An operator call marked against the company's criteria (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md
 * §2.3), and a supervisor's review of it.
 *
 * @param score            what counts now; null when no criterion applied to the call
 * @param judgedScore      the judge's own score, kept when a supervisor corrects the answers
 * @param sentiment        how the client came across, as the judge read it; null if it did not say
 * @param topics           what the call was about — the judge's one to three short lowercase tags
 * @param needsReview      whether the call went to the review inbox — a score under the
 *                         company's threshold, or an upset client
 * @param reviewStatus     null for a call that was not flagged and nobody reviewed
 * @param reviewedBy       the reviewing user
 * @param promptTokens     what judging the call cost — charged to the call with its analysis
 */
public record CallScorecard(
        long callId,
        long companyId,
        Long operatorUserId,
        Integer score,
        Integer judgedScore,
        Sentiment sentiment,
        List<String> topics,
        boolean needsReview,
        ScorecardReviewStatus reviewStatus,
        String reviewNote,
        Long reviewedBy,
        Instant reviewedAt,
        List<CallScorecardAnswer> answers,
        long promptTokens,
        long completionTokens,
        long cachedTokens,
        Instant createdAt
) {

    public CallScorecard {
        topics = topics == null ? List.of() : List.copyOf(topics);
        answers = answers == null ? List.of() : List.copyOf(answers);
    }

    /**
     * The judge's scorecard to save, in the inbox when {@code needsReview}; company and creation
     * time are the repository's to stamp.
     */
    public static CallScorecard judged(long callId, Long operatorUserId, Integer score, boolean needsReview,
                                       ScorecardVerdict verdict) {
        return new CallScorecard(callId, 0, operatorUserId, score, score, verdict.sentiment(), verdict.topics(), needsReview,
                needsReview ? ScorecardReviewStatus.PENDING : null, null, null, null, verdict.answers(),
                verdict.promptTokens(), verdict.completionTokens(), verdict.cachedTokens(), null);
    }

    /** The same call as a supervisor left it: their answers, the score they give, and who and when. */
    public CallScorecard withReview(List<CallScorecardAnswer> reviewedAnswers, Integer reviewedScore,
                                    ScorecardReviewStatus status, String note, Long userId, Instant at) {
        return new CallScorecard(callId, companyId, operatorUserId, reviewedScore, judgedScore, sentiment, topics,
                needsReview, status, note, userId, at, reviewedAnswers, promptTokens, completionTokens,
                cachedTokens, createdAt);
    }
}
