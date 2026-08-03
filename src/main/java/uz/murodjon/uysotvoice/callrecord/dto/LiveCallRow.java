package uz.murodjon.uysotvoice.callrecord.dto;

import java.time.Instant;

/**
 * One row of {@code GET /api/calls/live} (§10.2/§10.3 UI-DESIGN.md "Jonli
 * qo'ng'iroqlar"). {@code startedAt} is sent rather than an elapsed duration — the
 * panel's timer ticks client-side every second, so a duration computed at response
 * time would already be stale by the time it renders.
 *
 * @param phone        dialled number, or null for a manual/test call with no campaign
 * @param clientName   debtor name from the call's facts, or null
 * @param campaignId   owning campaign, or null for a manual/test call
 * @param campaignName owning campaign's name, or null
 * @param dialogState  FSM state name (e.g. {@code DEBT_NOTICE})
 */
public record LiveCallRow(
        String channelId,
        String phone,
        String clientName,
        Long campaignId,
        String campaignName,
        String language,
        Instant startedAt,
        String dialogState
) {
}
