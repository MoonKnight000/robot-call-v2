package uz.murodjon.robotcallv2.dialer.domain.entity;

import uz.murodjon.robotcallv2.dialer.domain.enums.ScrubStatus;

/**
 * Result of scrubbing a destination telephone number before call origination.
 */
public record ScrubResult(
        boolean valid,
        ScrubStatus status,
        String normalizedPhone,
        String reason,
        String operatorOrRegion
) {
    public static ScrubResult valid(String normalizedPhone, String operatorOrRegion) {
        return new ScrubResult(true, ScrubStatus.VALID, normalizedPhone, null, operatorOrRegion);
    }

    public static ScrubResult invalid(ScrubStatus status, String reason) {
        return new ScrubResult(false, status, null, reason, null);
    }
}
