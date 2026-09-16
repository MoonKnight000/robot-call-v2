package uz.murodjon.robotcallv2.live.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Operator guidance injected into a live call; the customer never hears it. */
public record WhisperRequest(@NotBlank @Size(max = 1000) String instruction) {
}
