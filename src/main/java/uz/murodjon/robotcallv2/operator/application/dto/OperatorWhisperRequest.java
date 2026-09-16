package uz.murodjon.robotcallv2.operator.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param message what the supervisor tells the bot — same limit as the live whisper's instruction */
public record OperatorWhisperRequest(@NotBlank @Size(max = 1000) String message) {
}
