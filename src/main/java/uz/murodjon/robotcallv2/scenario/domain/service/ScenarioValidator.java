package uz.murodjon.robotcallv2.scenario.domain.service;

import uz.murodjon.robotcallv2.scenario.domain.entity.*;
import uz.murodjon.robotcallv2.scenario.domain.enums.StageType;
import uz.murodjon.robotcallv2.shared.dialog.Disclosure;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Structural checks a ScenarioDefinition must pass before it can be saved.
 */
public final class ScenarioValidator {

    private static final Set<String> RESERVED_TOOL_NAMES = Set.of(
            "transitionTo", "endCall", "requestHumanTransfer", "recordWrongPerson", "recordDoNotCall",
            "extractVariables");

    private static final Set<String> ALLOWED_WEBHOOK_METHODS = Set.of("GET", "POST");

    /** Two seconds. This wait sits inside the dispatch loop, ahead of every other target's call. */
    static final int MAX_FACT_WEBHOOK_TIMEOUT_MS = 2000;

    /** Digits with an optional leading +: a transfer goes out through a SIP trunk, which takes nothing else. */
    private static final Pattern TRANSFER_NUMBER = Pattern.compile("\\+?[0-9]{3,15}");

    /** DTMF tones and {@code w}, the half-second pause an IVR needs between menu levels. */
    private static final Pattern DTMF_DIGITS = Pattern.compile("[0-9*#w]{1,32}");

    /** Ten SMS segments; a longer text is not a message a caller reads during a call. */
    private static final int MAX_SMS_TEXT_LENGTH = 1600;

    private static final int MAX_CODE_LENGTH = 20_000;

    private ScenarioValidator() {
    }

    public static List<String> validate(ScenarioDefinition def) {
        List<String> errors = new ArrayList<>();
        if (def == null) {
            errors.add("definition must not be null");
            return errors;
        }
        checkStages(def.stages(), errors);
        checkStart(def, errors);
        StageDef start = def.findStartStage();
        checkBranches(def.stages(), start, errors);
        checkTransitionConditions(def.stages(), errors);
        checkStaticLines(start, errors);
        checkStageTypes(def, errors);
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
     * The stage named by {@code startStageId} must exist, and must be one a call can open on: a
     * conversation, because the call opens with the agent's first message already spoken and
     * the caller answering it; and not a global one, which is reached by its condition, not by
     * the call starting.
     */
    private static void checkStart(ScenarioDefinition def, List<String> errors) {
        if (def.stages() == null || def.stages().isEmpty()) {
            return;
        }
        if (def.startStageId() != null && def.findStage(def.startStageId()) == null) {
            errors.add("startStageId " + def.startStageId() + " is not a stage");
            return;
        }
        StageDef start = def.findStartStage();
        if (start.type() != StageType.CONVERSATION) {
            errors.add("the start stage " + start.id() + " must be a CONVERSATION stage, not " + start.type());
        }
        if (start.globalCondition() != null) {
            errors.add("the start stage " + start.id() + " cannot be global");
        }
    }

    /**
     * A branch is a transition the code takes instead of the model, so it may only take one
     * the graph already allows. The start stage cannot branch: the call opens on it with
     * the greeting already spoken, before any transition the branches would ride on.
     * {@code ALWAYS} holds for every call, so a branch after it would never be taken.
     */
    private static void checkBranches(List<StageDef> stages, StageDef start, List<String> errors) {
        if (stages == null || stages.isEmpty()) {
            return;
        }
        for (StageDef s : stages) {
            if (s.branches() == null || s.branches().isEmpty()) {
                continue;
            }
            if (s == start) {
                errors.add("the start stage " + s.id() + " cannot have branches");
                continue;
            }
            for (int i = 0; i < s.branches().size(); i++) {
                StageBranch branch = s.branches().get(i);
                if (branch == null || branch.operator() == null
                        || (branch.operator().needsFact() && (branch.fact() == null || branch.fact().isBlank()))) {
                    errors.add("a branch of stage " + s.id() + " needs 'fact' and 'operator'");
                } else if (!branch.operator().needsFact() && i != s.branches().size() - 1) {
                    errors.add("the ALWAYS branch of stage " + s.id() + " must be its last");
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
     * A condition's target is already an allowed transition ({@link StageDef}); an empty one
     * tells the model nothing about when to take it.
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
                if (condition.getValue() == null || condition.getValue().isBlank()) {
                    errors.add("transition condition of stage " + s.id() + " for " + condition.getKey()
                            + " must not be empty");
                }
            }
        }
    }

    /**
     * The call opens on the start stage with the agent's own first message already spoken,
     * so a line of its own there would either never be said or be said twice.
     */
    private static void checkStaticLines(StageDef start, List<String> errors) {
        if (start == null) {
            return;
        }
        if (start.staticLine() != null && !start.staticLine().isBlank()) {
            errors.add("the start stage " + start.id() + " cannot have a staticLine — the agent's firstMessage opens the call");
        }
    }

    /**
     * What each {@link StageType} needs to do its job. A stage that only does one thing and
     * passes the call on must have somewhere to pass it; an ending must not; a note takes no
     * part in the graph at all, so nothing may lead into it either.
     */
    private static void checkStageTypes(ScenarioDefinition def, List<String> errors) {
        if (def.stages() == null) {
            return;
        }
        Set<String> notes = new HashSet<>();
        Set<String> outcomes = new HashSet<>();
        def.stages().stream().filter(s -> s.type() == StageType.NOTE).forEach(s -> notes.add(s.id()));
        if (def.outcomeSchema() != null) {
            def.outcomeSchema().forEach(o -> outcomes.add(o.name()));
        }
        for (StageDef s : def.stages()) {
            boolean hasNext = s.allowedTransitions() != null && !s.allowedTransitions().isEmpty();
            if (s.type().passesCallOn() && !hasNext) {
                errors.add(s.type() + " stage " + s.id() + " needs a transition to pass the call on");
            }
            if ((s.type() == StageType.ENDING || s.type() == StageType.NOTE) && hasNext) {
                errors.add(s.type() + " stage " + s.id() + " cannot have transitions");
            }
            if (hasNext) {
                for (String target : s.allowedTransitions()) {
                    if (notes.contains(target)) {
                        errors.add("stage " + s.id() + " transitions to NOTE " + target);
                    }
                }
            }
            if (s.globalCondition() != null && (s.globalCondition().isBlank() || s.type() == StageType.NOTE)) {
                errors.add("global stage " + s.id() + " needs a non-empty globalCondition and cannot be a NOTE");
            }
            checkStageAction(s, outcomes, errors);
        }
    }

    private static void checkStageAction(StageDef s, Set<String> outcomes, List<String> errors) {
        StageAction action = s.action();
        switch (s.type()) {
            case LOGIC_SPLIT -> {
                if (s.branches() == null || s.branches().isEmpty()
                        || s.branches().getLast() == null || s.branches().getLast().operator() == null
                        || s.branches().getLast().operator().needsFact()) {
                    errors.add("LOGIC_SPLIT stage " + s.id() + " needs branches ending with an ALWAYS branch");
                }
                if (s.transitionConditions() != null && !s.transitionConditions().isEmpty()) {
                    errors.add("LOGIC_SPLIT stage " + s.id() + " decides by branches only, not transitionConditions");
                }
            }
            case FUNCTION -> {
                if (action == null || isBlank(action.toolName())) {
                    errors.add("FUNCTION stage " + s.id() + " needs action.toolName");
                }
            }
            case MCP -> {
                if (action == null || action.mcpConnectionId() == null || isBlank(action.toolName())) {
                    errors.add("MCP stage " + s.id() + " needs action.mcpConnectionId and action.toolName");
                }
            }
            case CALL_TRANSFER -> {
                if (action == null || isBlank(action.transferTo()) || !TRANSFER_NUMBER.matcher(action.transferTo().trim()).matches()) {
                    errors.add("CALL_TRANSFER stage " + s.id() + " needs action.transferTo as a phone number");
                }
            }
            case PRESS_DIGIT -> {
                if (action == null || isBlank(action.digits()) || !DTMF_DIGITS.matcher(action.digits()).matches()) {
                    errors.add("PRESS_DIGIT stage " + s.id() + " needs action.digits of 0-9, *, # and w");
                }
            }
            case AGENT_TRANSFER -> {
                if (action == null || action.targetAgentId() == null) {
                    errors.add("AGENT_TRANSFER stage " + s.id() + " needs action.targetAgentId");
                }
            }
            case SMS -> {
                if (action == null || isBlank(action.smsText())) {
                    errors.add("SMS stage " + s.id() + " needs action.smsText");
                } else if (action.smsText().length() > MAX_SMS_TEXT_LENGTH) {
                    errors.add("SMS stage " + s.id() + " text must not exceed " + MAX_SMS_TEXT_LENGTH + " characters");
                }
            }
            case EXTRACT_VARIABLE -> {
                if (action == null || action.extractFields() == null || action.extractFields().isEmpty()) {
                    errors.add("EXTRACT_VARIABLE stage " + s.id() + " needs action.extractFields");
                } else {
                    for (String field : action.extractFields()) {
                        if (!outcomes.contains(field)) {
                            errors.add("EXTRACT_VARIABLE stage " + s.id() + " extracts " + field
                                    + ", which is not in outcomeSchema");
                        }
                    }
                }
            }
            case CODE -> {
                if (action == null || isBlank(action.code())) {
                    errors.add("CODE stage " + s.id() + " needs action.code");
                } else if (action.code().length() > MAX_CODE_LENGTH) {
                    errors.add("CODE stage " + s.id() + " code must not exceed " + MAX_CODE_LENGTH + " characters");
                }
            }
            case CONVERSATION, ENDING, NOTE -> {
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
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

        // A note is never entered: it neither ends a call nor needs a way out.
        Set<String> terminals = new HashSet<>();
        for (StageDef s : stages) {
            if (s.type() != StageType.NOTE && (s.allowedTransitions() == null || s.allowedTransitions().isEmpty())) {
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
            if (s.type() != StageType.NOTE && !canReachTerminal(s.id(), byId, terminals, new HashSet<>())) {
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
