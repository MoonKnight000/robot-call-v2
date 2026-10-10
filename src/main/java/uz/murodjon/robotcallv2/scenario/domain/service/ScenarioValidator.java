package uz.murodjon.robotcallv2.scenario.domain.service;

import uz.murodjon.robotcallv2.scenario.domain.entity.*;
import uz.murodjon.robotcallv2.shared.dialog.Disclosure;

import java.util.*;

/**
 * Structural checks a ScenarioDefinition must pass before it can be saved.
 */
public final class ScenarioValidator {

    private static final Set<String> RESERVED_TOOL_NAMES = Set.of(
            "transitionTo", "endCall", "requestHumanTransfer", "recordWrongPerson", "recordDoNotCall");

    private static final Set<String> ALLOWED_WEBHOOK_METHODS = Set.of("GET", "POST");

    /** Two seconds. This wait sits inside the dispatch loop, ahead of every other target's call. */
    static final int MAX_FACT_WEBHOOK_TIMEOUT_MS = 2000;

    private ScenarioValidator() {
    }

    public static List<String> validate(ScenarioDefinition def) {
        List<String> errors = new ArrayList<>();
        if (def == null) {
            errors.add("definition must not be null");
            return errors;
        }
        checkStages(def.stages(), errors);
        checkBranches(def.stages(), errors);
        checkTransitionConditions(def.stages(), errors);
        checkStaticLines(def.stages(), errors);
        checkRequiredOutcomes(def.stages(), errors);
        checkNamesUnique("fact", def.factSchema() == null ? List.of()
                : def.factSchema().stream().map(FactField::name).toList(), errors);
        checkTools(def.tools(), errors);
        checkStageTools(def.stages(), def.tools(), errors);
        checkOutcome(def.outcomeSchema(), errors);
        checkStyleExamples(def.styleExamples(), errors);
        checkGlobalNodes(def.globalNodes(), errors);
        checkDisclosure(def.disclosureText(), errors);
        checkFactWebhook(def.factWebhook(), def.factSchema(), errors);
        return errors;
    }

    /**
     * What can be judged from the definition alone. Whether the host is one this server is
     * allowed to call is decided when the call is actually made — a name that resolves
     * publicly today can resolve to a private address tomorrow, so the answer cannot be
     * cached in a saved scenario.
     */
    private static void checkFactWebhook(FactWebhook webhook, List<FactField> factSchema, List<String> errors) {
        if (webhook == null) {
            return;
        }
        if (webhook.url() == null || webhook.url().isBlank()) {
            errors.add("factWebhook.url must not be empty");
        } else {
            String url = webhook.url().trim().toLowerCase(Locale.ROOT);
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                errors.add("factWebhook.url must start with http:// or https://");
            }
        }
        if (webhook.method() != null && !webhook.method().isBlank()
                && !ALLOWED_WEBHOOK_METHODS.contains(webhook.method().trim().toUpperCase(Locale.ROOT))) {
            errors.add("factWebhook.method must be GET or POST");
        }
        if (webhook.timeoutMs() > MAX_FACT_WEBHOOK_TIMEOUT_MS) {
            errors.add("factWebhook.timeoutMs must not exceed " + MAX_FACT_WEBHOOK_TIMEOUT_MS
                    + " — the dialer waits for this between claiming a target and dialling it");
        }
        if (factSchema == null || factSchema.isEmpty()) {
            errors.add("factWebhook needs a factSchema: only declared facts are read from its response, "
                    + "so with an empty schema it would fetch and discard");
        }
    }

    private static void checkDisclosure(String disclosureText, List<String> errors) {
        if (disclosureText == null || disclosureText.isBlank()) {
            return;
        }
        if (!Disclosure.discloses(disclosureText)) {
            errors.add("disclosureText must state that the call is automated and that it is recorded (§11.1) "
                    + "— leave it empty to use the company's own wording");
        }
    }

    /**
     * A branch is a transition the code takes instead of the model, so it may only take one
     * the graph already allows. The first stage cannot branch: the call opens on it with
     * the greeting already spoken, before any transition the branches would ride on.
     */
    private static void checkBranches(List<StageDef> stages, List<String> errors) {
        if (stages == null || stages.isEmpty()) {
            return;
        }
        for (int i = 0; i < stages.size(); i++) {
            StageDef s = stages.get(i);
            if (s.branches() == null || s.branches().isEmpty()) {
                continue;
            }
            if (i == 0) {
                errors.add("the first stage " + s.id() + " cannot have branches");
                continue;
            }
            for (StageBranch branch : s.branches()) {
                if (branch == null || branch.fact() == null || branch.fact().isBlank() || branch.operator() == null) {
                    errors.add("a branch of stage " + s.id() + " needs 'fact' and 'operator'");
                } else if (branch.operator().needsValue() && (branch.value() == null || branch.value().isBlank())) {
                    errors.add("branch on " + branch.fact() + " in stage " + s.id() + " needs a 'value' for "
                            + branch.operator());
                } else if (s.allowedTransitions() == null || !s.allowedTransitions().contains(branch.goTo())) {
                    errors.add("branch of stage " + s.id() + " goes to " + branch.goTo()
                            + ", which is not in its allowedTransitions");
                }
            }
        }
    }

    /**
     * A condition only says when an allowed transition is taken; one on a stage the graph
     * does not lead to would read to the model as permission to go there, and be refused.
     */
    private static void checkTransitionConditions(List<StageDef> stages, List<String> errors) {
        if (stages == null) {
            return;
        }
        for (StageDef s : stages) {
            if (s.transitionConditions() == null) {
                continue;
            }
            for (Map.Entry<String, String> condition : s.transitionConditions().entrySet()) {
                if (s.allowedTransitions() == null || !s.allowedTransitions().contains(condition.getKey())) {
                    errors.add("transition condition of stage " + s.id() + " is for " + condition.getKey()
                            + ", which is not in its allowedTransitions");
                } else if (condition.getValue() == null || condition.getValue().isBlank()) {
                    errors.add("transition condition of stage " + s.id() + " for " + condition.getKey()
                            + " must not be empty");
                }
            }
        }
    }

    /**
     * The call opens on the first stage with the agent's own first message already spoken,
     * so a line of its own there would either never be said or be said twice.
     */
    private static void checkStaticLines(List<StageDef> stages, List<String> errors) {
        if (stages == null || stages.isEmpty()) {
            return;
        }
        StageDef first = stages.get(0);
        if (first.staticLine() != null && !first.staticLine().isBlank()) {
            errors.add("the first stage " + first.id() + " cannot have a staticLine — the agent's firstMessage opens the call");
        }
    }

    /** A stage with nowhere to go has nothing to hold the call back from. */
    private static void checkRequiredOutcomes(List<StageDef> stages, List<String> errors) {
        if (stages == null) {
            return;
        }
        for (StageDef s : stages) {
            if (s.requiredOutcomes() == null || s.requiredOutcomes().isEmpty()) {
                continue;
            }
            if (s.allowedTransitions() == null || s.allowedTransitions().isEmpty()) {
                errors.add("terminal stage " + s.id() + " cannot have requiredOutcomes — the call never leaves it");
            }
            for (String name : s.requiredOutcomes()) {
                if (name == null || name.isBlank()) {
                    errors.add("stage " + s.id() + " has a blank required outcome");
                }
            }
        }
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
            return;
        }

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
            return false;
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
            if (RESERVED_TOOL_NAMES.contains(t.name())) {
                errors.add("tool name '" + t.name() + "' is reserved for the fixed universal tool set");
            }
            if (t.params() == null) {
                continue;
            }
            checkNamesUnique("tool " + t.name() + " param",
                    t.params().stream().map(ToolParamDef::name).toList(), errors);
        }
    }

    private static void checkStageTools(List<StageDef> stages, List<ToolDef> tools, List<String> errors) {
        if (stages == null) {
            return;
        }
        Set<String> known = new HashSet<>(RESERVED_TOOL_NAMES);
        if (tools != null) {
            tools.forEach(t -> known.add(t.name()));
        }
        for (StageDef s : stages) {
            if (s.allowedTools() == null) {
                continue;
            }
            for (String toolName : s.allowedTools()) {
                if (!known.contains(toolName)) {
                    errors.add("stage " + s.id() + " allows unknown tool " + toolName);
                }
            }
        }
    }

    private static void checkOutcome(List<OutcomeField> outcome, List<String> errors) {
        if (outcome == null || outcome.isEmpty()) {
            errors.add("outcomeSchema must not be empty — a scenario with no outcome fields records nothing");
            return;
        }
        checkNamesUnique("outcome field", outcome.stream().map(OutcomeField::name).toList(), errors);
        if (outcome.stream().filter(OutcomeField::goal).count() > 1) {
            errors.add("only one outcome field may be the goal — a call reaches one goal or none");
        }
    }

    private static void checkStyleExamples(List<StyleExample> examples, List<String> errors) {
        if (examples == null) {
            return;
        }
        for (StyleExample example : examples) {
            if (example == null || example.wrong() == null || example.wrong().isBlank()
                    || example.right() == null || example.right().isBlank()) {
                errors.add("a style example needs both 'wrong' and 'right'");
            }
        }
    }

    private static void checkGlobalNodes(List<GlobalNode> nodes, List<String> errors) {
        if (nodes == null) {
            return;
        }
        for (GlobalNode node : nodes) {
            if (node == null || node.question() == null || node.question().isBlank()
                    || node.answer() == null || node.answer().isBlank()) {
                errors.add("a global node needs both 'question' and 'answer'");
            }
        }
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
