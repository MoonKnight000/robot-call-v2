package uz.murodjon.robotcallv2.dialer.application.dto;

import java.util.Map;

public record InstantCallRequest(
        Long campaignId,
        String phone,
        String clientName,
        Map<String, Object> contextData,
        Long clientId
) {
    public InstantCallRequest(Long campaignId, String phone, String clientName, Map<String, Object> contextData) {
        this(campaignId, phone, clientName, contextData, null);
    }
}
