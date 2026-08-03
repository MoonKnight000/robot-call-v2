package uz.murodjon.uysotvoice.profile.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * {@code PUT /api/profile/call-columns} body — whole-list replace of the calls table
 * "Ustunlar ⚙" choice (API-REQUIREMENTS §4). Column keys are frontend-owned; the
 * backend does not validate them against a fixed set.
 */
public record UpdateCallColumnsRequest(
        @NotNull @Size(max = 50) List<@Size(max = 60) String> columns
) {
}
