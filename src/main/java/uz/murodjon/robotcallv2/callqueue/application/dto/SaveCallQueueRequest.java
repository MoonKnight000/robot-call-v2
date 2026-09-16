package uz.murodjon.robotcallv2.callqueue.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueueMember;
import uz.murodjon.robotcallv2.callqueue.domain.enums.CallQueueStrategy;
import uz.murodjon.robotcallv2.callqueue.domain.enums.QueueOverflowAction;

import java.util.List;

/**
 * Body of POST/PUT /api/call-queues. Every null takes the default listed in
 * docs/api/call-queues.md; the meaning of each field is on {@code CallQueue}.
 */
public record SaveCallQueueRequest(
        @NotBlank String name,
        CallQueueStrategy strategy,
        Integer ringTimeoutSec,
        Integer wrapUpSec,
        Integer maxWaitSec,
        Integer maxLength,
        Integer announcePositionEverySec,
        Boolean announceWaitTime,
        String periodicAnnounceText,
        Integer periodicAnnounceEverySec,
        Long holdMusicFileId,
        Integer callbackOfferAfterSec,
        Long callbackCampaignId,
        QueueOverflowAction overflowAction,
        String overflowTarget,
        Integer serviceLevelSec,
        Integer stickySeconds,
        Boolean postCallSurvey,
        Long waitingAgentId,
        Boolean autoCallback,
        Boolean enabled,
        List<@Valid CallQueueMember> members
) {
}
