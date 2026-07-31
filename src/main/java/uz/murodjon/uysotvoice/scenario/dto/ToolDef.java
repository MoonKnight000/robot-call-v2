package uz.murodjon.uysotvoice.scenario.dto;

import java.util.List;

/**
 * One scenario-specific tool declaration (ROADMAP A.1), e.g. "recordPaymentPromise:
 * date (must be in the future), amount, note". Additive only — the fixed universal
 * tools ({@code transitionTo}, {@code endCall}, {@code requestHumanTransfer},
 * {@code recordWrongPerson}, {@code recordDoNotCall}) are not declared here; every
 * scenario gets them regardless.
 *
 * @param name        tool name, as the LLM must call it
 * @param description what it records and when to call it
 * @param params      its arguments
 */
public record ToolDef(String name, String description, List<ToolParamDef> params) {
}
