package uz.murodjon.uysotvoice.scenario.dto;

import uz.murodjon.uysotvoice.scenario.service.ScenarioValidator;

import java.util.List;

/**
 * A scenario's full behavior (ROADMAP A.1): stages, facts, tools, outcome shape, role
 * prompt, and additional guardrails. Stored as {@code scenario.definition} JSONB.
 *
 * <p><strong>Scoping boundary:</strong> this pass only stores and structurally
 * validates this shape (see {@link ScenarioValidator}) — {@link
 * uz.murodjon.uysotvoice.agent.dialog.DialogEngine} still runs the hardcoded
 * debt-collection FSM verbatim, and {@code DialogState}/{@code CallContext}/{@code
 * DialogTools} are untouched. Wiring a scenario's own definition into live call
 * execution, and binding a campaign to a {@code scenario.id}, are separate, later
 * changes (ROADMAP A.3).
 *
 * <p>{@code guardrails} here is <em>additive only</em>: the platform-level
 * prohibitions and the §11.1 mandatory disclosure are enforced in code
 * ({@code DialogEngine.speakDisclosure}, {@code FactGuard}) regardless of what a
 * scenario declares, and no custom scenario can remove or soften them (ROADMAP A.4).
 *
 * @param stages        FSM states and their allowed transitions
 * @param factSchema    facts this scenario needs, for the prompt and fact guard
 * @param tools         scenario-specific tools, in addition to the fixed universal set
 *                      ({@code transitionTo}, {@code endCall}, {@code requestHumanTransfer},
 *                      {@code recordWrongPerson}, {@code recordDoNotCall})
 * @param outcomeSchema fields the final call outcome/summary records
 * @param rolePrompt    "Siz ... agentisiz" — the agent's role/persona
 * @param guardrails    additional rules on top of the fixed platform ones
 * @param disclosureText the §11.1 opening disclosure text this scenario intends to use
 */
public record ScenarioDefinition(
        List<StageDef> stages,
        List<FactField> factSchema,
        List<ToolDef> tools,
        List<OutcomeField> outcomeSchema,
        String rolePrompt,
        List<String> guardrails,
        String disclosureText
) {
}
