package uz.murodjon.robotcallv2.scenario.domain.entity;

import java.util.List;

/**
 * What a call says: the stages, the facts it may use, the tools it may call and the rules
 * it must keep to.
 *
 * <p>Who says it is not here — that is the {@code AiAgent} pointing at this scenario (V12).
 * The two were briefly merged, when the Agent Builder table was folded into this record as
 * an {@code agentProfile}, and the merge was wrong in both directions: a scenario is shared
 * and versioned, while the voice a company reads it in is neither, so one company changing
 * a voice would have meant a new version of everybody's script.
 */
public record ScenarioDefinition(
        List<StageDef> stages,
        List<FactField> factSchema,
        List<ToolDef> tools,
        List<OutcomeField> outcomeSchema,
        String rolePrompt,
        List<String> guardrails,
        String disclosureText,
        /** Where to refresh {@code factSchema} values from just before dialling; null = don't. */
        FactWebhook factWebhook
) {
    /** A scenario whose facts come only from the campaign's own data and the CRM. */
    public ScenarioDefinition(List<StageDef> stages, List<FactField> factSchema, List<ToolDef> tools,
                              List<OutcomeField> outcomeSchema, String rolePrompt, List<String> guardrails,
                              String disclosureText) {
        this(stages, factSchema, tools, outcomeSchema, rolePrompt, guardrails, disclosureText, null);
    }

    /**
     * The same scenario spoken with a different role prompt — what an A/B variant's
     * {@code promptOverride} produces.
     *
     * <p>Only the role prompt is replaced. The stages, facts, tools and guardrails are the
     * scenario's contract with the rest of the system, and a variant that could rewrite
     * those would be a different scenario, which is what {@code variant.aiAgentId} is for.
     *
     * @param prompt the replacement; null or blank returns this definition unchanged
     */
    public ScenarioDefinition withRolePrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return this;
        }
        return new ScenarioDefinition(stages, factSchema, tools, outcomeSchema, prompt, guardrails,
                disclosureText, factWebhook);
    }

    public StageDef findStage(String id) {
        if (id == null || stages == null) {
            return null;
        }
        for (StageDef s : stages) {
            if (id.equalsIgnoreCase(s.id())) {
                return s;
            }
        }
        return null;
    }
}
