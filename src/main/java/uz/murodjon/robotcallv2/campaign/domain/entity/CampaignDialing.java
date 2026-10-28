package uz.murodjon.robotcallv2.campaign.domain.entity;

import uz.murodjon.robotcallv2.campaign.domain.enums.DialMode;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetOrdering;

/**
 * Who talks to the clients and in which order they are dialled.
 *
 * @param mode                 who talks to the clients; never null, {@link DialMode#AI} when not given
 * @param callQueueId          the operator queue whose members take the calls; required for every mode but AI
 * @param maxAiOverflowPercent PREDICTIVE: the share of answered calls the AI agent may take, over the
 *                             last half hour, before the dialer slows to one call per free operator
 * @param targetOrdering       which due targets the dialer claims first; never null,
 *                             {@link TargetOrdering#FIFO} when not given
 */
public record CampaignDialing(
        DialMode mode,
        Long callQueueId,
        int maxAiOverflowPercent,
        TargetOrdering targetOrdering
) {

    public CampaignDialing {
        mode = mode == null ? DialMode.AI : mode;
        targetOrdering = targetOrdering == null ? TargetOrdering.FIFO : targetOrdering;
    }
}
