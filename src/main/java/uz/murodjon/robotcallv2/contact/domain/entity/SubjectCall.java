package uz.murodjon.robotcallv2.contact.domain.entity;

import java.time.Instant;

/**
 * One call a company made to, or took from, a phone number — as a data-subject export
 * lists it (NEXT-PLAN S.7).
 *
 * @param startedAt   null for an attempt that never reached the trunk
 * @param disposition how the call ended, or null while it had none
 * @param summary     the post-call summary, or null when none was written
 */
public record SubjectCall(
        long callId,
        Instant startedAt,
        Integer durationSec,
        String disposition,
        String summary
) {
}
