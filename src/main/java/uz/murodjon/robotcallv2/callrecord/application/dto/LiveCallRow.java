package uz.murodjon.robotcallv2.callrecord.application.dto;

import java.time.Instant;

/**
 * One row of GET /api/calls/live (§10.2/§10.3 UI-DESIGN.md "Jonli qo'ng'iroqlar").
 *
 * @param inbound   the caller rang in (inbound route), rather than being dialled
 * @param agentId   the AI agent speaking on the call, or null
 * @param agentName that agent's name, or null
 */
public record LiveCallRow(
        String channelId,
        String phone,
        String clientName,
        Long campaignId,
        String campaignName,
        String language,
        Instant startedAt,
        String dialogState,
        String trunk,
        boolean inbound,
        Long agentId,
        String agentName
) {
}
