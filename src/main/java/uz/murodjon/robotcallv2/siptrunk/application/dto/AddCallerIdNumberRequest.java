package uz.murodjon.robotcallv2.siptrunk.application.dto;

import jakarta.validation.constraints.NotBlank;

public record AddCallerIdNumberRequest(
        @NotBlank String phone,
        String label
) {
}
