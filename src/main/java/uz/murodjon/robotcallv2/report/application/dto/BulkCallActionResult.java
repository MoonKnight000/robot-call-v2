package uz.murodjon.robotcallv2.report.application.dto;

import java.util.List;

/**
 * Outcome of {@code POST /api/reports/calls/bulk} — a bad id (wrong company, or no
 * matching target/phone) is reported here rather than failing the whole request,
 * matching the CSV-import convention elsewhere in the codebase.
 *
 * @param processed how many ids the action was applied to
 * @param failed    ids that could not be acted on
 */
public record BulkCallActionResult(int processed, List<Long> failed) {
}

