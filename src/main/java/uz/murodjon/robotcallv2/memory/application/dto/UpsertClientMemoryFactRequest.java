package uz.murodjon.robotcallv2.memory.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A fact an operator sets by hand; the agent hears it on the next call like any remembered fact. */
public record UpsertClientMemoryFactRequest(
        @NotBlank @Size(max = 500) String value
) {
}
