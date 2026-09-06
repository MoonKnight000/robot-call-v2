package uz.murodjon.robotcallv2.dialer.application.dto;

import java.util.Map;

public record InstantCallRequest(
        Long campaignId,
        String phone,
        String clientName,
        Map<String, Object> contextData
) {
}
