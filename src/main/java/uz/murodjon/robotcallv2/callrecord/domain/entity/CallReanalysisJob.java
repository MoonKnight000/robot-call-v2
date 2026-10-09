package uz.murodjon.robotcallv2.callrecord.domain.entity;

import uz.murodjon.robotcallv2.callrecord.domain.enums.CallReanalysisStatus;

import java.time.Instant;

/**
 * Runs an agent's current analysis settings ({@code dataNeeded} / {@code dataEvaluation})
 * over its finished calls in a time range, replacing that part of each call's stored
 * outcome. Nothing else a call set off — the CRM note, a callback, post-call webhooks — is
 * repeated.
 *
 * @param from           inclusive, on {@code call_attempt.started_at}
 * @param to             exclusive
 * @param totalCalls     the calls in range when the job was queued
 * @param processedCalls the calls gone through so far, whatever happened to each
 * @param updatedCalls   of those, the ones whose outcome was rewritten
 * @param failedCalls    of those, the ones the model gave no answer for; their outcome is unchanged
 * @param lastCallId     the worker's cursor: calls are taken in id order after this one
 * @param chargedUzs     what the job has taken off the balance so far
 * @param lastError      why a {@code FAILED} job stopped
 */
public record CallReanalysisJob(
        Long id,
        long companyId,
        long aiAgentId,
        Instant from,
        Instant to,
        CallReanalysisStatus status,
        int totalCalls,
        int processedCalls,
        int updatedCalls,
        int failedCalls,
        long promptTokens,
        long completionTokens,
        long chargedUzs,
        long lastCallId,
        String lastError,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {

    /** A new job, before the worker has seen it; the adapter stamps id and createdAt. */
    public static CallReanalysisJob queued(long companyId, long aiAgentId, Instant from, Instant to, int totalCalls) {
        return new CallReanalysisJob(null, companyId, aiAgentId, from, to, CallReanalysisStatus.QUEUED, totalCalls,
                0, 0, 0, 0, 0, 0, 0, null, null, null, null);
    }
}
