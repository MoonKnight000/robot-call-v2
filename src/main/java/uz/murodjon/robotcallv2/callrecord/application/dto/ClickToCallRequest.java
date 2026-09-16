package uz.murodjon.robotcallv2.callrecord.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;

/** Body of POST /api/calls/click-to-call — the number the operator wants to ring. */
public record ClickToCallRequest(@NotBlank @Pattern(regexp = PhoneNumbers.E164_REGEX) String phone) {
}
