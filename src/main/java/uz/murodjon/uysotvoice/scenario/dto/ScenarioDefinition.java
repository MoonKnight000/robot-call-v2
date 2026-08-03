package uz.murodjon.uysotvoice.scenario.dto;

import uz.murodjon.uysotvoice.scenario.service.ScenarioValidator;

import java.util.List;

/**
 * A scenario's full behavior (ROADMAP A.1): stages, facts, tools, outcome shape, role
 * prompt, and additional guardrails. Stored as {@code scenario.definition} JSONB, and
 * — as of ROADMAP A.3 — what {@link uz.murodjon.uysotvoice.agent.dialog.DialogEngine}
 * actually runs for a call: the bound {@code campaign.scenario_id} (or a manual test
 * call's chosen/default scenario) is resolved once and reused for the whole call, per
 * {@link ScenarioValidator} having passed it beforehand.
 *
 * <p>{@code guardrails} here is <em>additive only</em>: the platform-level
 * prohibitions and the §11.1 mandatory disclosure are enforced in code
 * ({@code DialogEngine.speakDisclosure}, {@code SystemPromptFactory}'s fixed
 * guardrail block) regardless of what a scenario declares, and no custom scenario can
 * remove or soften them (ROADMAP A.4).
 *
 * @param stages        FSM states and their allowed transitions; the first entry is the
 *                      call's entry stage, by convention
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
