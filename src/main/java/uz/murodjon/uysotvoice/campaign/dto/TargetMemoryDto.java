package uz.murodjon.uysotvoice.campaign.dto;

import java.util.Map;

/**
 * Memory and manual operator notes attached to a campaign target.
 */
public record TargetMemoryDto(
        long targetId,
        String operatorNotes,
        String lastCallSummary,
        String preferredName,
        Map<String, Object> contextData
) {
}
