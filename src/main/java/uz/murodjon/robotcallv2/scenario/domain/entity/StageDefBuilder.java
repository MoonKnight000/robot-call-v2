package uz.murodjon.robotcallv2.scenario.domain.entity;

import uz.murodjon.robotcallv2.scenario.domain.enums.StageType;

import java.util.List;
import java.util.Map;

/**
 * Collects a {@link StageDef}'s fields; obtained from {@link StageDef#builder()} or, to change
 * a few fields of an existing stage, {@link StageDef#toBuilder()}. Every optional field not set
 * stays null, which is how a stage says "none" — see the field docs on {@link StageDef}.
 */
public class StageDefBuilder {

    private String id;
    private String purpose;
    private List<String> allowedTransitions;
    private List<String> allowedTools;
    private String emotion;
    private String model;
    private List<StageBranch> branches;
    private Map<String, String> transitionConditions;
    private String staticLine;
    private List<String> requiredOutcomes;
    private StageType type;
    private StagePosition position;
    private String globalCondition;
    private StageAction action;
    private String title;
    private StageOverrides overrides;
    private List<StyleExample> examples;
    private Boolean skipWhenKnown;
    private List<Long> knowledgeSourceIds;

    StageDefBuilder() {
    }

    StageDefBuilder(StageDef source) {
        this.id = source.id();
        this.purpose = source.purpose();
        this.allowedTransitions = source.allowedTransitions();
        this.allowedTools = source.allowedTools();
        this.emotion = source.emotion();
        this.model = source.model();
        this.branches = source.branches();
        this.transitionConditions = source.transitionConditions();
        this.staticLine = source.staticLine();
        this.requiredOutcomes = source.requiredOutcomes();
        this.type = source.type();
        this.position = source.position();
        this.globalCondition = source.globalCondition();
        this.action = source.action();
        this.title = source.title();
        this.overrides = source.overrides();
        this.examples = source.examples();
        this.skipWhenKnown = source.skipWhenKnown();
        this.knowledgeSourceIds = source.knowledgeSourceIds();
    }

    public StageDefBuilder id(String id) {
        this.id = id;
        return this;
    }

    public StageDefBuilder purpose(String purpose) {
        this.purpose = purpose;
        return this;
    }

    public StageDefBuilder allowedTransitions(List<String> allowedTransitions) {
        this.allowedTransitions = allowedTransitions;
        return this;
    }

    public StageDefBuilder allowedTools(List<String> allowedTools) {
        this.allowedTools = allowedTools;
        return this;
    }

    public StageDefBuilder emotion(String emotion) {
        this.emotion = emotion;
        return this;
    }

    public StageDefBuilder model(String model) {
        this.model = model;
        return this;
    }

    public StageDefBuilder branches(List<StageBranch> branches) {
        this.branches = branches;
        return this;
    }

    public StageDefBuilder transitionConditions(Map<String, String> transitionConditions) {
        this.transitionConditions = transitionConditions;
        return this;
    }

    public StageDefBuilder staticLine(String staticLine) {
        this.staticLine = staticLine;
        return this;
    }

    public StageDefBuilder requiredOutcomes(List<String> requiredOutcomes) {
        this.requiredOutcomes = requiredOutcomes;
        return this;
    }

    public StageDefBuilder type(StageType type) {
        this.type = type;
        return this;
    }

    public StageDefBuilder position(StagePosition position) {
        this.position = position;
        return this;
    }

    public StageDefBuilder globalCondition(String globalCondition) {
        this.globalCondition = globalCondition;
        return this;
    }

    public StageDefBuilder action(StageAction action) {
        this.action = action;
        return this;
    }

    public StageDefBuilder title(String title) {
        this.title = title;
        return this;
    }

    public StageDefBuilder overrides(StageOverrides overrides) {
        this.overrides = overrides;
        return this;
    }

    public StageDefBuilder examples(List<StyleExample> examples) {
        this.examples = examples;
        return this;
    }

    public StageDefBuilder skipWhenKnown(Boolean skipWhenKnown) {
        this.skipWhenKnown = skipWhenKnown;
        return this;
    }

    public StageDefBuilder knowledgeSourceIds(List<Long> knowledgeSourceIds) {
        this.knowledgeSourceIds = knowledgeSourceIds;
        return this;
    }

    public StageDef build() {
        return new StageDef(id, purpose, allowedTransitions, allowedTools, emotion, model, branches,
                transitionConditions, staticLine, requiredOutcomes, type, position, globalCondition, action, title,
                overrides, examples, skipWhenKnown, knowledgeSourceIds);
    }
}
