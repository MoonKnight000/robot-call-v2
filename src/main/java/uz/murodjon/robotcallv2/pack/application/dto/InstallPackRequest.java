package uz.murodjon.robotcallv2.pack.application.dto;

import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/packs/{key}/install}. Both fields are optional: without {@code catalogUrl}
 * the catalog is added later through {@code POST /api/knowledge-base/sources}.
 *
 * @param catalogUrl       the company's product feed (CSV or JSON), for a pack that has a catalog
 * @param catalogItemsPath where the rows are in a JSON feed ({@code $.data}); null for a top-level array
 */
public record InstallPackRequest(
        @Size(max = 2048) String catalogUrl,
        @Size(max = 200) String catalogItemsPath
) {
}
