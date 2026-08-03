package uz.murodjon.uysotvoice.search.dto;

import java.util.List;

/**
 * {@code GET /api/search?q=} response (UI-DESIGN §11.9 command palette). "Sahifalar"
 * (pages) and "Buyruqlar" (commands) are client-side only — static nav/command lists —
 * so this only ever returns the two groups backed by real data.
 */
public record SearchResult(List<SearchItem> campaigns, List<SearchItem> calls) {
}
