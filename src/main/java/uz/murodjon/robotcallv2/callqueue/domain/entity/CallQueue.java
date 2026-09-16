package uz.murodjon.robotcallv2.callqueue.domain.entity;

import uz.murodjon.robotcallv2.callqueue.domain.enums.CallQueueStrategy;
import uz.murodjon.robotcallv2.callqueue.domain.enums.QueueOverflowAction;

import java.time.Instant;
import java.util.List;

/**
 * An operator queue (docs/INBOUND-PBX-PLAN.md §7.3). Every timer is in seconds; 0 switches
 * it off.
 *
 * @param ringTimeoutSec           how long one operator rings before the next is tried
 * @param wrapUpSec                after-call time before the operator is offered the next call
 * @param maxWaitSec               longest a caller waits before overflow
 * @param maxLength                callers that may wait at once; 0 = no limit
 * @param announcePositionEverySec how often "sizning navbatingiz N-chi" is repeated
 * @param announceWaitTime         add the estimated wait when it is reliable enough to say
 * @param periodicAnnounceText     said every {@code periodicAnnounceEverySec} while waiting
 * @param holdMusicFileId          a WAV to wait to; null plays the built-in melody
 * @param callbackOfferAfterSec    after this long, offer "1 ni bosing — qayta qo'ng'iroq qilamiz"
 * @param callbackCampaignId       the campaign an accepted callback joins; null puts it on the
 *                                 missed-call list instead
 * @param overflowTarget           read by {@code overflowAction}: an id or a number
 * @param serviceLevelSec          answered within this long counts toward SLA
 * @param stickySeconds            ring the operator who last spoke to this caller alone for
 *                                 this long before the rest of the queue
 * @param postCallSurvey           ask for a 1–5 rating after the operator hangs up
 * @param waitingAgentId           an AI agent that talks to the caller while they wait
 * @param autoCallback             call back this queue's missed callers by itself
 */
public record CallQueue(
        long id,
        long companyId,
        String name,
        CallQueueStrategy strategy,
        int ringTimeoutSec,
        int wrapUpSec,
        int maxWaitSec,
        int maxLength,
        int announcePositionEverySec,
        boolean announceWaitTime,
        String periodicAnnounceText,
        int periodicAnnounceEverySec,
        Long holdMusicFileId,
        int callbackOfferAfterSec,
        Long callbackCampaignId,
        QueueOverflowAction overflowAction,
        String overflowTarget,
        int serviceLevelSec,
        int stickySeconds,
        boolean postCallSurvey,
        Long waitingAgentId,
        boolean autoCallback,
        boolean enabled,
        List<CallQueueMember> members,
        Instant createdAt
) {
    public CallQueue {
        if (strategy == null) {
            strategy = CallQueueStrategy.LEAST_RECENT;
        }
        if (overflowAction == null) {
            overflowAction = QueueOverflowAction.CALLBACK;
        }
        members = members == null ? List.of() : List.copyOf(members);
    }

    /** {@code overflowTarget} as an id — for QUEUE, AI_AGENT and VOICEMAIL; null when it is not one. */
    public Long overflowTargetId() {
        if (overflowTarget == null || overflowTarget.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(overflowTarget.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
