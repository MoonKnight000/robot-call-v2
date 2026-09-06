package uz.murodjon.robotcallv2.memory.application.dto;

import jakarta.validation.constraints.Size;

/**
 * Operator-maintained part of a client's memory. {@code null} leaves a field as it
 * is, blank clears it.
 */
public record UpdateClientMemoryRequest(
        @Size(max = 100) String preferredName,
        @Size(max = 10) String preferredLanguage,
        @Size(max = 2000) String operatorNotes
) {
}
