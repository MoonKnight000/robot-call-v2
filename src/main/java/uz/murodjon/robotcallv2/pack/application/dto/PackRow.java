package uz.murodjon.robotcallv2.pack.application.dto;

import uz.murodjon.robotcallv2.pack.domain.entity.PackSecret;

import java.util.List;

/**
 * One pack in the catalog ({@code GET /api/packs}): enough to choose it and to know what
 * has to be filled in after installing it.
 *
 * @param hasCatalogFeed whether the agent searches a product catalog — the install request's
 *                       {@code catalogUrl} is then worth giving
 */
public record PackRow(
        String key,
        String name,
        String description,
        String industry,
        List<String> toolNames,
        boolean hasCatalogFeed,
        int testCaseCount,
        List<PackSecret> secrets
) {
}
