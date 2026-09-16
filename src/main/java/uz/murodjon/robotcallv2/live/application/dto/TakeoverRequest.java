package uz.murodjon.robotcallv2.live.application.dto;

import jakarta.validation.constraints.Pattern;

/**
 * Hand a live call to a human; {@code extension} is optional and may be null. When given
 * it is the SIP extension ({@code User.sipExtension}) of an operator of the call's own
 * company who has a SIP account, and the call rings that operator's phone. Digits only.
 */
public record TakeoverRequest(@Pattern(regexp = "[0-9]{2,8}") String extension) {
}
