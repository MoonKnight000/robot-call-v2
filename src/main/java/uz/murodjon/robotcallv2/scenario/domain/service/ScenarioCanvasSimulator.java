package uz.murodjon.robotcallv2.scenario.domain.service;

import uz.murodjon.robotcallv2.scenario.application.dto.CanvasDryRunRequest;
import uz.murodjon.robotcallv2.scenario.application.dto.CanvasDryRunResponse;
import uz.murodjon.robotcallv2.scenario.domain.entity.GlobalNode;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageBranch;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Node-level dry-run execution simulator for the Visual Scenario Studio.
 *
 * <p>Allows visual canvas users to click any node, provide test caller utterances and facts,
 * and preview node transitions, branch rules, and formatted bot responses in real-time.
 */
public final class ScenarioCanvasSimulator {

    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)(?:\\s*\\|\\s*\"([^\"]*)\")?\\s*\\}\\}");

    private ScenarioCanvasSimulator() {
    }

    /**
     * Executes a single dry-run turn on the scenario definition.
     */
    public static CanvasDryRunResponse step(ScenarioDefinition definition, CanvasDryRunRequest request) {
        if (definition == null || definition.stages() == null || definition.stages().isEmpty()) {
            return new CanvasDryRunResponse(
                    request.currentNodeId(),
                    request.currentNodeId(),
                    null,
                    "Scenario stages are empty",
                    "NO_STAGES",
                    request.facts() != null ? request.facts() : Map.of(),
                    request.currentOutcomes() != null ? request.currentOutcomes() : Map.of(),
                    true,
                    List.of(),
                    List.of()
            );
        }

        Map<String, Object> facts = request.facts() != null ? new LinkedHashMap<>(request.facts()) : new LinkedHashMap<>();
        Map<String, Object> outcomes = request.currentOutcomes() != null ? new LinkedHashMap<>(request.currentOutcomes()) : new LinkedHashMap<>();

        // 1. Resolve current stage
        String currentStageId = request.currentNodeId();
        StageDef currentStage = null;
        if (currentStageId != null && !currentStageId.isBlank()) {
            currentStage = definition.findStage(currentStageId);
        }
        if (currentStage == null) {
            currentStage = definition.findStartStage();
            currentStageId = currentStage.id();
        }

        String userInput = request.userInput() != null ? request.userInput().trim() : "";

        // 2. Global Node Interrupt Check (caller asks FAQ question at any time)
        if (!userInput.isEmpty() && definition.globalNodes() != null) {
            GlobalNode matchedGlobal = findMatchingGlobalNode(definition.globalNodes(), userInput);
            if (matchedGlobal != null) {
                return new CanvasDryRunResponse(
                        currentStageId,
                        currentStageId,
                        null,
                        renderTemplate(matchedGlobal.answer(), facts),
                        "Global FAQ interrupt matched: \"" + matchedGlobal.question() + "\"",
                        facts,
                        outcomes,
                        false,
                        List.of(currentStageId),
                        currentStage.allowedTransitions() != null ? currentStage.allowedTransitions() : List.of()
                );
            }
        }

        // 3. Stage Deterministic Branches (evaluated on facts before model turn)
        if (currentStage.branches() != null && !currentStage.branches().isEmpty()) {
            String branchTarget = StageBranchEvaluator.findTarget(currentStage.branches(), facts::get);
            if (branchTarget != null && !branchTarget.equalsIgnoreCase(currentStageId)) {
                StageDef targetStage = definition.findStage(branchTarget);
                String activeEdge = "e_branch_" + currentStageId + "_" + branchTarget;
                String botSpeech = targetStage != null && targetStage.staticLine() != null
                        ? renderTemplate(targetStage.staticLine(), facts)
                        : (targetStage != null ? targetStage.purpose() : "Branch to " + branchTarget);

                boolean terminal = targetStage == null || targetStage.allowedTransitions() == null || targetStage.allowedTransitions().isEmpty();
                List<String> available = targetStage != null && targetStage.allowedTransitions() != null
                        ? targetStage.allowedTransitions() : List.of();

                return new CanvasDryRunResponse(
                        currentStageId,
                        branchTarget,
                        activeEdge,
                        botSpeech,
                        "Deterministic branch condition satisfied -> " + branchTarget,
                        facts,
                        outcomes,
                        terminal,
                        List.of(currentStageId, branchTarget),
                        available
                );
            }
        }

        // 4. Initial entry (no user input yet, bot speaks current stage)
        if (userInput.isEmpty()) {
            String speech = currentStage.staticLine() != null
                    ? renderTemplate(currentStage.staticLine(), facts)
                    : currentStage.purpose();

            boolean terminal = currentStage.allowedTransitions() == null || currentStage.allowedTransitions().isEmpty();
            List<String> available = currentStage.allowedTransitions() != null ? currentStage.allowedTransitions() : List.of();

            return new CanvasDryRunResponse(
                    currentStageId,
                    currentStageId,
                    null,
                    speech,
                    "Current stage prompt",
                    facts,
                    outcomes,
                    terminal,
                    List.of(currentStageId),
                    available
            );
        }

        // 5. Evaluate Transition to Next Stage
        List<String> allowed = currentStage.allowedTransitions() != null ? currentStage.allowedTransitions() : List.of();
        if (allowed.isEmpty()) {
            // Already in terminal stage
            return new CanvasDryRunResponse(
                    currentStageId,
                    currentStageId,
                    null,
                    "Suhbat yakunlandi (terminal bosqich).",
                    "Terminal stage reached: no outgoing transitions",
                    facts,
                    outcomes,
                    true,
                    List.of(currentStageId),
                    List.of()
            );
        }

        // Determine target transition
        String nextStageId = selectNextStage(currentStage, userInput, allowed);
        StageDef nextStage = definition.findStage(nextStageId);
        String activeEdgeId = "e_" + currentStageId + "_to_" + nextStageId;

        // Auto-record outcomes if required by next stage or current stage
        if (currentStage.requiredOutcomes() != null) {
            for (String req : currentStage.requiredOutcomes()) {
                if (!outcomes.containsKey(req)) {
                    outcomes.put(req, userInput);
                }
            }
        }

        String botSpeech = nextStage != null && nextStage.staticLine() != null
                ? renderTemplate(nextStage.staticLine(), facts)
                : (nextStage != null ? nextStage.purpose() : "O'tildi: " + nextStageId);

        boolean isTerminal = nextStage == null || nextStage.allowedTransitions() == null || nextStage.allowedTransitions().isEmpty();
        List<String> nextAvailable = nextStage != null && nextStage.allowedTransitions() != null
                ? nextStage.allowedTransitions() : List.of();

        return new CanvasDryRunResponse(
                currentStageId,
                nextStageId,
                activeEdgeId,
                botSpeech,
                "Transition matched -> " + nextStageId,
                facts,
                outcomes,
                isTerminal,
                List.of(currentStageId, nextStageId),
                nextAvailable
        );
    }

    private static String selectNextStage(StageDef stage, String userInput, List<String> allowed) {
        if (allowed.size() == 1) {
            return allowed.get(0);
        }

        Map<String, String> conditions = stage.transitionConditions();
        if (conditions != null) {
            String lowerInput = userInput.toLowerCase(Locale.ROOT);
            for (Map.Entry<String, String> entry : conditions.entrySet()) {
                String target = entry.getKey();
                String cond = entry.getValue().toLowerCase(Locale.ROOT);
                // Look for common keywords
                if (matchesCondition(lowerInput, cond)) {
                    return target;
                }
            }
        }

        // Fallback to first allowed transition
        return allowed.get(0);
    }

    private static boolean matchesCondition(String input, String condition) {
        // Simple keyword heuristic for simulation dry-run
        String[] words = condition.split("\\s+");
        for (String w : words) {
            if (w.length() >= 4 && input.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private static GlobalNode findMatchingGlobalNode(List<GlobalNode> globalNodes, String input) {
        String lowerInput = input.toLowerCase(Locale.ROOT);
        for (GlobalNode gn : globalNodes) {
            String q = gn.question().toLowerCase(Locale.ROOT);
            if (lowerInput.contains(q)) {
                return gn;
            }
            // Check significant words
            String[] words = q.split("\\s+");
            int matches = 0;
            for (String w : words) {
                if (w.length() >= 3 && lowerInput.contains(w)) {
                    matches++;
                }
            }
            if (matches >= 2 || (words.length == 1 && matches == 1)) {
                return gn;
            }
        }
        return null;
    }

    /**
     * Replaces {{variable}} and {{variable | "fallback"}} with values from the facts map.
     */
    public static String renderTemplate(String template, Map<String, Object> facts) {
        if (template == null || facts == null || facts.isEmpty()) {
            return template;
        }

        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String fallback = matcher.group(2);

            Object val = facts.get(varName);
            String replacement;
            if (val != null) {
                replacement = val.toString();
            } else if (fallback != null) {
                replacement = fallback;
            } else {
                replacement = matcher.group(0); // keep {{varName}}
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
