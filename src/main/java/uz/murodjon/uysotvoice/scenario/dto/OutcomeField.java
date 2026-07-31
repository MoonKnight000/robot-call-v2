package uz.murodjon.uysotvoice.scenario.dto;

/**
 * One field of a scenario's final call outcome/summary (ROADMAP A.1) — the
 * declarative replacement for the debt-specific {@code call_result} columns.
 *
 * @param name        outcome key, e.g. {@code "promisedDate"}
 * @param type        {@code "string"}, {@code "number"}, {@code "boolean"}, {@code "date"},
 *                    or {@code "array"}
 * @param description what this field means, for reporting/UI
 */
public record OutcomeField(String name, String type, String description) {
}
