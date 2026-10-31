package uz.murodjon.robotcallv2.phonenumber.application.dto;

import jakarta.validation.constraints.NotNull;

/** The company's own trunk a BYON number is on ({@code PUT /api/phone-numbers/{id}/trunk}). */
public record UpdatePhoneNumberTrunkRequest(
        @NotNull Long trunkId
) {
}
