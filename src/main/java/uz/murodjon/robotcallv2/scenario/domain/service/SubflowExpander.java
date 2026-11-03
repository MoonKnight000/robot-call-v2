package uz.murodjon.robotcallv2.scenario.domain.service;

import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageBranch;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.scenario.domain.entity.SubflowDef;
import uz.murodjon.robotcallv2.scenario.domain.enums.StageType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Flattens a scenario's subflows into its own stage list, so that nothing that runs a call (the turn
 * runner, the transition checks, the realtime engine) has to know subflows exist.
 *
 * <p>Each {@code SUBFLOW} stage is replaced by a copy of its subflow's stages, ids prefixed with the
 * embedding stage's id ({@code collect__ask_name}) so one subflow can be embedded more than once.
 * Edges that pointed at the embedding stage now point at the subflow's start; every stage of the
 * subflow that has no way on (and is not an {@code ENDING}) takes over the embedding stage's own
 * transitions, which is how the call leaves it.
 */
public final class SubflowExpander {

    /** Joins the embedding stage's id and an inner stage's id in the flattened flow. */
    public static final String SEPARATOR = "__";

    private SubflowExpander() {
    }

    /**
     * @return the scenario with every {@code SUBFLOW} stage inlined and {@code subflows} emptied, or
     *         {@code definition} itself when it embeds none. A {@code SUBFLOW} stage naming no known (or
     *         an empty) subflow is left as it is: {@link ScenarioValidator} refuses it on save
     */
    public static ScenarioDefinition expand(ScenarioDefinition definition) {
        if (definition == null || definition.stages() == null) {
            return definition;
        }
        // Embedding stage id → the flattened id of its subflow's start, which is where its edges now go.
        Map<String, String> entries = new HashMap<>();
        for (StageDef stage : definition.stages()) {
            SubflowDef subflow = findEmbedded(definition, stage);
            if (subflow != null && subflow.findStartStage() != null) {
                entries.put(key(stage.id()), stage.id() + SEPARATOR + subflow.findStartStage().id());
            }
        }
        if (entries.isEmpty()) {
            return definition;
        }
        UnaryOperator<String> toEntry = id -> id == null ? null : entries.getOrDefault(key(id), id);

        List<StageDef> flattened = new ArrayList<>();
        for (StageDef stage : definition.stages()) {
            SubflowDef subflow = findEmbedded(definition, stage);
            if (subflow != null && subflow.findStartStage() != null) {
                flattened.addAll(inline(subflow, stage, toEntry));
            } else {
                flattened.add(retarget(stage, toEntry));
            }
        }
        return definition.toBuilder()
                .stages(flattened)
                .startStageId(toEntry.apply(definition.startStageId()))
                .subflows(List.of())
                .build();
    }

    /** The subflow a stage embeds, or null when it is not a usable SUBFLOW stage. */
    private static SubflowDef findEmbedded(ScenarioDefinition definition, StageDef stage) {
        if (stage.type() != StageType.SUBFLOW || stage.action() == null) {
            return null;
        }
        SubflowDef subflow = definition.findSubflow(stage.action().subflowId());
        return subflow != null && subflow.findStartStage() != null ? subflow : null;
    }

    /**
     * The subflow's stages as part of the parent flow, the start first (a flow without a
     * {@code startStageId} opens on its first stage). {@code toEntry} maps the embedding stage's own
     * targets, since the way on may lead into another subflow.
     */
    private static List<StageDef> inline(SubflowDef subflow, StageDef block, UnaryOperator<String> toEntry) {
        String prefix = block.id() + SEPARATOR;
        UnaryOperator<String> inner = id -> id == null ? null : prefix + id;

        List<String> onwards = mapAll(block.allowedTransitions(), toEntry);
        Map<String, String> onwardConditions = mapKeys(block.transitionConditions(), toEntry);

        StageDef start = subflow.findStartStage();
        List<StageDef> ordered = new ArrayList<>();
        ordered.add(start);
        for (StageDef stage : subflow.stages()) {
            if (stage != start) {
                ordered.add(stage);
            }
        }

        List<StageDef> inlined = new ArrayList<>();
        for (StageDef stage : ordered) {
            StageDef moved = retarget(stage, inner).toBuilder()
                    .id(prefix + stage.id())
                    // The subflow's canvas is its own; the parent canvas has no place for these.
                    .position(null)
                    .build();
            if (isExit(stage)) {
                moved = moved.toBuilder()
                        .allowedTransitions(onwards)
                        .transitionConditions(onwardConditions)
                        .build();
            }
            inlined.add(moved);
        }
        return inlined;
    }

    /** A stage the call leaves the subflow from: it has nowhere to go and does not end the call. */
    static boolean isExit(StageDef stage) {
        boolean endsCall = stage.type() == StageType.ENDING || stage.type() == StageType.NOTE;
        return !endsCall && (stage.allowedTransitions() == null || stage.allowedTransitions().isEmpty());
    }

    /** The stage with every stage id it refers to (transitions, conditions, branch targets) run through {@code mapper}. */
    private static StageDef retarget(StageDef stage, UnaryOperator<String> mapper) {
        return stage.toBuilder()
                .allowedTransitions(mapAll(stage.allowedTransitions(), mapper))
                .transitionConditions(mapKeys(stage.transitionConditions(), mapper))
                .branches(stage.branches() == null ? null : stage.branches().stream()
                        .map(branch -> branch == null ? null
                                : new StageBranch(branch.fact(), branch.operator(), branch.value(), mapper.apply(branch.goTo())))
                        .toList())
                .build();
    }

    private static List<String> mapAll(List<String> ids, UnaryOperator<String> mapper) {
        if (ids == null) {
            return null;
        }
        Set<String> mapped = new LinkedHashSet<>();
        for (String id : ids) {
            mapped.add(mapper.apply(id));
        }
        return new ArrayList<>(mapped);
    }

    private static Map<String, String> mapKeys(Map<String, String> byId, UnaryOperator<String> mapper) {
        if (byId == null || byId.isEmpty()) {
            return null;
        }
        Map<String, String> mapped = new LinkedHashMap<>();
        byId.forEach((id, value) -> mapped.put(mapper.apply(id), value));
        return mapped;
    }

    private static String key(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT);
    }
}
