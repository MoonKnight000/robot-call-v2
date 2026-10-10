package uz.murodjon.robotcallv2.scenario.domain.entity;

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

    public StageDef build() {
        return new StageDef(id, purpose, allowedTransitions, allowedTools, emotion, model, branches,
                transitionConditions, staticLine, requiredOutcomes);
    }
}
