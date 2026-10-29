package uz.murodjon.robotcallv2.phonenumber.application.dto;

import jakarta.validation.constraints.NotBlank;

public record TestSipConnectionRequest(
        @NotBlank String host,
        Integer port,
        String username,
        String password,
        String transport
) {
}
