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
        FactWebhook factWebhook,
        /** The scenario's own ✗ → ✓ phrasing pairs for the prompt (UNIVERSAL-OPERATOR-PLAN A.2); null = none. */
        List<StyleExample> styleExamples,
        /** Questions answered at any stage without leaving it ({@link GlobalNode}); null = none. */
        List<GlobalNode> globalNodes,
        /**
         * What this call is about, in a sentence: "the client left a loan request on the site;
         * go through it". Said to the model ahead of everything else, so a debt call, a
         * reminder and a lead follow-up cannot be told apart only by guessing from the role
         * prompt; null = the role prompt and the stages are all it has.
         */
        String callPurpose
) {
    public static ScenarioDefinitionBuilder builder() {
        return new ScenarioDefinitionBuilder();
    }

    /** A builder that starts from this scenario, for changing a few of its fields. */
    public ScenarioDefinitionBuilder toBuilder() {
        return new ScenarioDefinitionBuilder(this);
    }

    /** A scenario whose facts come only from the campaign's own data and the CRM. */
    public ScenarioDefinition(List<StageDef> stages, List<FactField> factSchema, List<ToolDef> tools,
                              List<OutcomeField> outcomeSchema, String rolePrompt, List<String> guardrails,
                              String disclosureText) {
        this(stages, factSchema, tools, outcomeSchema, rolePrompt, guardrails, disclosureText, null, null, null, null);
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
        return toBuilder().rolePrompt(prompt).build();
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

    /**
     * The name of the outcome field this scenario exists to record ({@link OutcomeField#goal}),
     * or null when it names none — and then a call's disposition is whatever its tools set.
     */
    public String findGoalField() {
        if (outcomeSchema == null) {
            return null;
        }
        for (OutcomeField field : outcomeSchema) {
            if (field.goal()) {
                return field.name();
            }
        }
        return null;
    }
}
