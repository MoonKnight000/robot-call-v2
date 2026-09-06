package uz.murodjon.robotcallv2.live.application.dto;

import jakarta.validation.constraints.NotBlank;

/** Operator guidance injected into a live call; the customer never hears it. */
public record WhisperRequest(@NotBlank String instruction) {
}
