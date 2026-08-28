package uz.murodjon.uysotvoice.campaign.dto;

import java.util.Map;

/**
 * Request payload for manually updating a target's memory and operator notes.
 */
public record UpdateTargetMemoryRequest(
        String operatorNotes,
        String lastCallSummary,
        String preferredName,
        Map<String, Object> additionalContext
) {
}
