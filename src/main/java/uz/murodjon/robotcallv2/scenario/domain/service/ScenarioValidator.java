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
        checkNamesUnique("fact", def.factSchema() == null ? List.of()
                : def.factSchema().stream().map(FactField::name).toList(), errors);
        checkTools(def.tools(), errors);
        checkStageTools(def.stages(), def.tools(), errors);
        checkOutcome(def.outcomeSchema(), errors);
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
