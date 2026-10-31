package uz.murodjon.robotcallv2.phonenumber.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A number the company already has on its own SIP trunk ({@code POST /api/phone-numbers/byo}).
 *
 * @param trunkId  one of the company's own trunks; the number's calls go out over it
 * @param nickname null names the number by itself
 */
public record CreateByoNumberRequest(
        @NotBlank String phoneNumber,
        @NotNull Long trunkId,
        @Size(max = 120) String nickname
) {
}
