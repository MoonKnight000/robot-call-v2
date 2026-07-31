package uz.murodjon.uysotvoice.scenario.dto;

import uz.murodjon.uysotvoice.shared.dialog.DialogState;

import java.util.List;

/**
 * One FSM state a scenario can be in (ROADMAP A.1) — the declarative replacement for
 * the hardcoded {@link uz.murodjon.uysotvoice.shared.dialog.DialogState} enum.
 *
 * @param id                 stable state id (e.g. {@code "GREETING"}, {@code "DEBT_NOTICE"})
 * @param purpose            what this stage tries to accomplish, folded into the prompt
 * @param allowedTransitions ids of stages {@code transitionTo} may move to from here;
 *                           empty/null marks this stage terminal (a call may end here)
 */
public record StageDef(String id, String purpose, List<String> allowedTransitions) {
}
