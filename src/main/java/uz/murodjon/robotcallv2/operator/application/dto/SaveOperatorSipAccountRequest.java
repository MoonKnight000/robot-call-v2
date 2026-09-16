package uz.murodjon.robotcallv2.operator.application.dto;

import jakarta.validation.constraints.Pattern;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;

/**
 * Body of PUT /api/operators/{userId}/sip-account.
 *
 * @param webrtc        true (default) for the browser softphone; false for a SIP phone
 * @param forwardNumber the operator's mobile, rung when the softphone is offline; null for none
 */
public record SaveOperatorSipAccountRequest(
        Boolean webrtc,
        @Pattern(regexp = PhoneNumbers.E164_REGEX) String forwardNumber
) {
}
