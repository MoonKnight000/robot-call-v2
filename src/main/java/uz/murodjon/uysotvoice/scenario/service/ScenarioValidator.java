package uz.murodjon.uysotvoice.scenario.service;

import uz.murodjon.uysotvoice.scenario.dto.FactField;
import uz.murodjon.uysotvoice.scenario.dto.OutcomeField;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioDefinition;
import uz.murodjon.uysotvoice.scenario.dto.StageDef;
import uz.murodjon.uysotvoice.scenario.dto.ToolDef;
import uz.murodjon.uysotvoice.scenario.dto.ToolParamDef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Structural checks a {@link ScenarioDefinition} must pass before it can be saved
 * (ROADMAP A.4): every stage can reach a terminal one (no deadlock), tool/outcome/fact
 * names don't collide, and every transition points at a real stage. A definition that
 * fails any of these is rejected with a 400, never partially saved.
 */
public final class ScenarioValidator {

    private ScenarioValidator() {
    }

    public static List<String> validate(ScenarioDefinition def) {
        List<String> errors = new ArrayList<>();
        if (def == null) {
            errors.add("definition must not be null");
            return errors;
        }
        checkStages(def.stages(), errors);
        checkNamesUnique("fact", def.factSchema() == null ? List.of()
                : def.factSchema().stream().map(FactField::name).toList(), errors);
        checkTools(def.tools(), errors);
        checkOutcome(def.outcomeSchema(), errors);
        return errors;
    }

    private static void checkStages(List<StageDef> stages, List<String> errors) {
        if (stages == null || stages.isEmpty()) {
            errors.add("stages must not be empty");
            return;
        }
        Set<String> ids = new HashSet<>();
        for (StageDef s : stages) {
            if (s.id() == null || s.id().isBlank()) {
                errors.add("a stage has a blank id");
            } else if (!ids.add(s.id())) {
                errors.add("duplicate stage id: " + s.id());
            }
        }
        for (StageDef s : stages) {
            if (s.allowedTransitions() == null) {
                continue;
            }
            for (String target : s.allowedTransitions()) {
                if (!ids.contains(target)) {
                    errors.add("stage " + s.id() + " transitions to unknown stage " + target);
                }
            }
        }
        if (!errors.isEmpty()) {
            return; // unknown/duplicate ids would make reachability meaningless
        }

        // Reachability (no deadlock): a stage with no outgoing transitions is terminal
        // — a call may end there. Every other stage must be able to reach one via BFS,
        // or a scenario could leave a call with no way to ever hang up.
        Set<String> terminals = new HashSet<>();
        for (StageDef s : stages) {
            if (s.allowedTransitions() == null || s.allowedTransitions().isEmpty()) {
                terminals.add(s.id());
            }
        }
        if (terminals.isEmpty()) {
            errors.add("no terminal stage (a stage with no outgoing transitions) — no call could ever end");
            return;
        }
        Map<String, StageDef> byId = new HashMap<>();
        for (StageDef s : stages) {
            byId.put(s.id(), s);
        }
        for (StageDef s : stages) {
            if (!canReachTerminal(s.id(), byId, terminals, new HashSet<>())) {
                errors.add("stage " + s.id() + " cannot reach any terminal stage (deadlock)");
            }
        }
    }

    private static boolean canReachTerminal(String from, Map<String, StageDef> byId,
                                            Set<String> terminals, Set<String> visited) {
        if (terminals.contains(from)) {
            return true;
        }
        if (!visited.add(from)) {
            return false; // already on this path — a cycle with no way out of it
        }
        StageDef stage = byId.get(from);
        if (stage == null || stage.allowedTransitions() == null) {
            return false;
        }
        for (String next : stage.allowedTransitions()) {
            if (canReachTerminal(next, byId, terminals, visited)) {
                return true;
            }
        }
        return false;
    }

    private static void checkTools(List<ToolDef> tools, List<String> errors) {
        if (tools == null) {
            return;
        }
        checkNamesUnique("tool", tools.stream().map(ToolDef::name).toList(), errors);
        for (ToolDef t : tools) {
            if (t.params() == null) {
                continue;
            }
            checkNamesUnique("tool " + t.name() + " param",
                    t.params().stream().map(ToolParamDef::name).toList(), errors);
        }
    }

    private static void checkOutcome(List<OutcomeField> outcome, List<String> errors) {
        if (outcome == null || outcome.isEmpty()) {
            errors.add("outcomeSchema must not be empty — a scenario with no outcome fields records nothing");
            return;
        }
        checkNamesUnique("outcome field", outcome.stream().map(OutcomeField::name).toList(), errors);
    }

    private static void checkNamesUnique(String what, List<String> names, List<String> errors) {
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                errors.add("a " + what + " has a blank name");
            } else if (!seen.add(name)) {
                errors.add("duplicate " + what + " name: " + name);
            }
        }
    }
}
