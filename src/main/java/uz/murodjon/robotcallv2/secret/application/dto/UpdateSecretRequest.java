package uz.murodjon.robotcallv2.secret.application.dto;

public record UpdateSecretRequest(
        String value,
        String description
) {
}
