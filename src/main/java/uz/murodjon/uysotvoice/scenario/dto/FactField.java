package uz.murodjon.uysotvoice.scenario.dto;

import uz.murodjon.uysotvoice.agent.dialog.CallContext;

/**
 * One fact a scenario needs injected into the prompt and checked by the fact guard
 * (ROADMAP A.1) — the declarative replacement for a hardcoded
 * {@link uz.murodjon.uysotvoice.agent.dialog.CallContext} field.
 *
 * @param name     fact key, matched against {@code campaign_target.context_data}
 * @param type     {@code "string"}, {@code "number"}, or {@code "date"}
 * @param required whether a call may start without this fact present
 */
public record FactField(String name, String type, boolean required) {
}
