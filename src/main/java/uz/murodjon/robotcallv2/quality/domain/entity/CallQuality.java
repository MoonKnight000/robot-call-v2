package uz.murodjon.robotcallv2.quality.domain.entity;

import uz.murodjon.robotcallv2.quality.domain.enums.CallReviewStatus;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;

/**
 * How one finished AI call went (UNIVERSAL-OPERATOR-PLAN D.1–D.2): the quality judge's verdict,
 * the signals read without an LLM, and a person's review.
 *
 * @param score              the judge's 0–100; null — with every flag below — when the judge
 *                           did not score the call (off, not sampled, failed)
 * @param needsReview        whether the judge found something a person should look at
 * @param repairCount        the caller's "what?" and "say that again" — a sign of speech that did not land
 * @param bargeInCount       times the caller cut the agent off, noise included
 * @param noInputPromptCount times the silence watchdog asked whether the caller was still there
 * @param lowConfidenceCount caller utterances the recognizer was unsure of; 0 on a realtime call
 * @param toolErrorCount     company tool requests that did not come back 2xx (UNIVERSAL-OPERATOR-PLAN B.6)
 * @param repeatCall         the same number called again within a day — the matter was likely not settled
 * @param reviewStatus       null when nothing was flagged and nobody reviewed the call
 * @param reviewedBy         the reviewing user; null for a review not made by a person's session
 * @param knowledgeItemId    the knowledge base answer written from this call, if any
 * @param testCaseId         the test case made from this call, if any
 */
public record CallQuality(
        Long id,
        long companyId,
        long callAttemptId,
        Long aiAgentId,
        Integer agentVersion,
        Disposition disposition,
        Integer score,
        Boolean guardrailViolation,
        Boolean languageMismatch,
        Boolean talkedOverClient,
        Boolean outcomeCorrect,
        Boolean goalReached,
        Boolean unansweredQuestion,
        Boolean toolMisuse,
        String note,
        boolean needsReview,
        int turnCount,
        int repairCount,
        int bargeInCount,
        int noInputPromptCount,
        int lowConfidenceCount,
        int toolErrorCount,
        boolean repeatCall,
        CallReviewStatus reviewStatus,
        String reviewReason,
        Long reviewedBy,
        Instant reviewedAt,
        Long knowledgeItemId,
        Long testCaseId,
        Instant createdAt
) {

    public CallQuality withReview(CallReviewStatus status, String reason, Long userId, Instant at) {
        return new CallQuality(id, companyId, callAttemptId, aiAgentId, agentVersion, disposition, score,
                guardrailViolation, languageMismatch, talkedOverClient, outcomeCorrect, goalReached,
                unansweredQuestion, toolMisuse, note, needsReview, turnCount, repairCount, bargeInCount,
                noInputPromptCount, lowConfidenceCount, toolErrorCount, repeatCall,
                status, reason, userId, at, knowledgeItemId, testCaseId, createdAt);
    }

    public CallQuality withKnowledgeItemId(long itemId) {
        return new CallQuality(id, companyId, callAttemptId, aiAgentId, agentVersion, disposition, score,
                guardrailViolation, languageMismatch, talkedOverClient, outcomeCorrect, goalReached,
                unansweredQuestion, toolMisuse, note, needsReview, turnCount, repairCount, bargeInCount,
                noInputPromptCount, lowConfidenceCount, toolErrorCount, repeatCall,
                reviewStatus, reviewReason, reviewedBy, reviewedAt, itemId, testCaseId, createdAt);
    }

    public CallQuality withTestCaseId(long caseId) {
        return new CallQuality(id, companyId, callAttemptId, aiAgentId, agentVersion, disposition, score,
                guardrailViolation, languageMismatch, talkedOverClient, outcomeCorrect, goalReached,
                unansweredQuestion, toolMisuse, note, needsReview, turnCount, repairCount, bargeInCount,
                noInputPromptCount, lowConfidenceCount, toolErrorCount, repeatCall,
                reviewStatus, reviewReason, reviewedBy, reviewedAt, knowledgeItemId, caseId, createdAt);
    }
}
