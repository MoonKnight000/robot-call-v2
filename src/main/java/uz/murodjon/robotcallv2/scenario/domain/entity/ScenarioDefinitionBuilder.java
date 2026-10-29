package uz.murodjon.robotcallv2.scenario.domain.entity;

import uz.murodjon.robotcallv2.scenario.domain.enums.TransitionMode;

import java.util.List;

/**
 * Collects a {@link ScenarioDefinition}'s fields; obtained from {@link ScenarioDefinition#builder()}
 * or, to change a few fields of an existing scenario, {@link ScenarioDefinition#toBuilder()}.
 * Every field not set stays null, which is how a scenario says "none" — see the field docs on
 * {@link ScenarioDefinition}.
 */
public class ScenarioDefinitionBuilder {

    private List<StageDef> stages;
    private List<FactField> factSchema;
    private List<ToolDef> tools;
    private List<OutcomeField> outcomeSchema;
    private String rolePrompt;
    private List<String> guardrails;
    private String disclosureText;
    private FactWebhook factWebhook;
    private List<StyleExample> styleExamples;
    private List<GlobalNode> globalNodes;
    private String callPurpose;

    ScenarioDefinitionBuilder() {
    }

    ScenarioDefinitionBuilder(ScenarioDefinition source) {
        this.stages = source.stages();
        this.factSchema = source.factSchema();
        this.tools = source.tools();
        this.outcomeSchema = source.outcomeSchema();
        this.rolePrompt = source.rolePrompt();
        this.guardrails = source.guardrails();
        this.disclosureText = source.disclosureText();
        this.factWebhook = source.factWebhook();
        this.styleExamples = source.styleExamples();
        this.globalNodes = source.globalNodes();
        this.callPurpose = source.callPurpose();
    }

    public ScenarioDefinitionBuilder stages(List<StageDef> stages) {
        this.stages = stages;
        return this;
    }

    public ScenarioDefinitionBuilder factSchema(List<FactField> factSchema) {
        this.factSchema = factSchema;
        return this;
    }

    public ScenarioDefinitionBuilder tools(List<ToolDef> tools) {
        this.tools = tools;
        return this;
    }

    public ScenarioDefinitionBuilder outcomeSchema(List<OutcomeField> outcomeSchema) {
        this.outcomeSchema = outcomeSchema;
        return this;
    }

    public ScenarioDefinitionBuilder rolePrompt(String rolePrompt) {
        this.rolePrompt = rolePrompt;
        return this;
    }

    public ScenarioDefinitionBuilder guardrails(List<String> guardrails) {
        this.guardrails = guardrails;
        return this;
    }

    public ScenarioDefinitionBuilder disclosureText(String disclosureText) {
        this.disclosureText = disclosureText;
        return this;
    }

    public ScenarioDefinitionBuilder factWebhook(FactWebhook factWebhook) {
        this.factWebhook = factWebhook;
        return this;
    }

    public ScenarioDefinitionBuilder styleExamples(List<StyleExample> styleExamples) {
        this.styleExamples = styleExamples;
        return this;
    }

    public ScenarioDefinitionBuilder globalNodes(List<GlobalNode> globalNodes) {
        this.globalNodes = globalNodes;
        return this;
    }

    public ScenarioDefinitionBuilder callPurpose(String callPurpose) {
        this.callPurpose = callPurpose;
        return this;
    }

    public ScenarioDefinitionBuilder startStageId(String startStageId) {
        this.startStageId = startStageId;
        return this;
    }

    public ScenarioDefinitionBuilder transitionMode(TransitionMode transitionMode) {
        this.transitionMode = transitionMode;
        return this;
    }

    public ScenarioDefinition build() {
        return new ScenarioDefinition(stages, factSchema, tools, outcomeSchema, rolePrompt, guardrails,
                disclosureText, factWebhook, styleExamples, globalNodes, callPurpose, startStageId, transitionMode);
    }
}
