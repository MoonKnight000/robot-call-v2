package uz.murodjon.robotcallv2.callrecord.application.dto;

import java.time.Instant;

/**
 * One row of GET /api/calls/live (§10.2/§10.3 UI-DESIGN.md "Jonli qo'ng'iroqlar").
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
        String trunk
) {
    public LiveCallRow(String channelId, String phone, String clientName, Long campaignId,
                       String campaignName, String language, Instant startedAt, String dialogState) {
        this(channelId, phone, clientName, campaignId, campaignName, language, startedAt, dialogState, null);
    }
}
