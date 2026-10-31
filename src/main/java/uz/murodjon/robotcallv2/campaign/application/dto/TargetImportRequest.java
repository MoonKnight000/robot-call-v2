package uz.murodjon.robotcallv2.campaign.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * A file of targets with the person's column mapping ({@code POST /api/campaigns/{id}/targets/import[/preview]}) —
 * Retell's "Map the columns" step. An .xlsx file arrives here already turned into CSV
 * ({@code POST /api/campaigns/{id}/targets/xlsx}).
 *
 * @param csv     the file's text, header row first
 * @param columns header → field: a known field ({@code phone}, {@code clientName}, …), a new fact name, or {@code ""}
 *                to leave the column out. Headers it does not name are matched as the plain CSV import matches them
 */
public record TargetImportRequest(
        @NotBlank @Size(max = 5_000_000) String csv,
        Map<String, String> columns
) {

    public TargetImportRequest {
        columns = columns == null ? Map.of() : columns;
    }
}
