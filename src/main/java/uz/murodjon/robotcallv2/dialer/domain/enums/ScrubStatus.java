package uz.murodjon.robotcallv2.dialer.domain.enums;

/**
 * Outcome status of pre-call phone number scrubbing.
 */
public enum ScrubStatus {
    VALID,
    INVALID_FORMAT,
    UNALLOCATED_PREFIX,
    UNREACHABLE,
    BLOCKED
}
