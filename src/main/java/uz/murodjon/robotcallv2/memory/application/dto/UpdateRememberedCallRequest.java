package uz.murodjon.robotcallv2.memory.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** An operator's correction of what the agent will hear about one past conversation. */
public record UpdateRememberedCallRequest(
        @NotBlank @Size(max = 1000) String summary
) {
}
