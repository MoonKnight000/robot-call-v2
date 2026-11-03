package uz.murodjon.robotcallv2.aiagent.domain.service;

import uz.murodjon.robotcallv2.scenario.domain.entity.GlobalNode;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageBranch;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.scenario.domain.enums.BranchOperator;
import uz.murodjon.robotcallv2.scenario.domain.enums.StageType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes a conversation flow out as one system prompt, for an agent converted to single-prompt
 * mode: the role prompt, what the call is about, every stage in the order a call reaches it with
 * where it goes next, the questions answered anywhere, and the rules. Subflows are expected
 * already inlined. Pure text — the model reads the stages as instructions, nothing enforces them.
 */
public final class FlowPromptWriter {

    private static final Map<BranchOperator, String> OPERATOR_SIGNS = Map.of(
            BranchOperator.EQUALS, "=",
            BranchOperator.NOT_EQUALS, "≠",
            BranchOperator.GREATER, ">",
            BranchOperator.GREATER_OR_EQUAL, "≥",
            BranchOperator.LESS, "<",
            BranchOperator.LESS_OR_EQUAL, "≤");

    private FlowPromptWriter() {
    }

    /**
     * @param fallbackRolePrompt the agent's own system prompt, used when the flow has no role prompt
     */
    public static String writePrompt(ScenarioDefinition definition, String fallbackRolePrompt) {
        StringBuilder prompt = new StringBuilder();
        String rolePrompt = isBlank(definition.rolePrompt()) ? fallbackRolePrompt : definition.rolePrompt();
        if (!isBlank(rolePrompt)) {
            prompt.append(rolePrompt.strip()).append("\n\n");
        }
        if (!isBlank(definition.callPurpose())) {
            prompt.append("## Qo'ng'iroq maqsadi\n").append(definition.callPurpose().strip()).append("\n\n");
        }

        List<StageDef> stages = orderStages(definition);
        Map<String, String> titles = new LinkedHashMap<>();
        stages.forEach(stage -> titles.put(stage.id(), titleOf(stage)));
        if (!stages.isEmpty()) {
            prompt.append("## Suhbat bosqichlari\nSuhbatni shu bosqichlar bo'yicha olib boring, birinchisidan boshlang.\n\n");
            for (int i = 0; i < stages.size(); i++) {
                writeStage(prompt, i + 1, stages.get(i), titles);
            }
        }

        List<GlobalNode> globalNodes = definition.globalNodes() == null ? List.of() : definition.globalNodes();
        if (!globalNodes.isEmpty()) {
            prompt.append("## Istalgan bosqichda beriladigan savollar\n");
            for (GlobalNode node : globalNodes) {
                prompt.append("- \"").append(node.question()).append("\" → ").append(node.answer()).append('\n');
            }
            prompt.append('\n');
        }

        List<String> guardrails = definition.guardrails() == null ? List.of() : definition.guardrails();
        if (!guardrails.isEmpty() || !isBlank(definition.disclosureText())) {
            prompt.append("## Qoidalar\n");
            guardrails.forEach(rule -> prompt.append("- ").append(rule).append('\n'));
            if (!isBlank(definition.disclosureText())) {
                prompt.append("- So'ralsa, shunday ayting: ").append(definition.disclosureText().strip()).append('\n');
            }
        }
        return prompt.toString().strip();
    }

    private static void writeStage(StringBuilder prompt, int number, StageDef stage, Map<String, String> titles) {
        StageType type = stage.type() == null ? StageType.CONVERSATION : stage.type();
        prompt.append("### ").append(number).append(". ").append(titles.get(stage.id())).append('\n');
        if (!isBlank(stage.globalCondition())) {
            prompt.append("Suhbatning istalgan joyida: ").append(stage.globalCondition().strip()).append(" — shu bosqichga o'ting.\n");
        }
        if (!isBlank(stage.purpose())) {
            prompt.append(stage.purpose().strip()).append('\n');
        }
        if (!isBlank(stage.staticLine())) {
            prompt.append("Aynan shunday ayting: \"").append(stage.staticLine().strip()).append("\"\n");
        }
        if (Boolean.TRUE.equals(stage.skipWhenKnown())) {
            prompt.append("Bu ma'lumot allaqachon ma'lum bo'lsa, bosqichni o'tkazib yuboring.\n");
        }
        switch (type) {
            case FUNCTION -> {
                if (stage.action() != null && !isBlank(stage.action().toolName())) {
                    prompt.append("`").append(stage.action().toolName()).append("` funksiyasini chaqiring.\n");
                }
            }
            case CALL_TRANSFER -> prompt.append("Qo'ng'iroqni operatorga ulang.\n");
            case AGENT_TRANSFER -> prompt.append("Qo'ng'iroqni boshqa agentga ulang.\n");
            case ENDING -> prompt.append("Xayrlashing va qo'ng'iroqni yakunlang (`endCall`).\n");
            default -> {
            }
        }
        if (stage.allowedTools() != null && !stage.allowedTools().isEmpty()) {
            prompt.append("Bu bosqichda ishlatish mumkin: ").append(String.join(", ", stage.allowedTools())).append(".\n");
        }
        writeNextSteps(prompt, stage, titles);
        prompt.append('\n');
    }

    private static void writeNextSteps(StringBuilder prompt, StageDef stage, Map<String, String> titles) {
        Set<String> described = new LinkedHashSet<>();
        for (StageBranch branch : stage.branches() == null ? List.<StageBranch>of() : stage.branches()) {
            prompt.append("- ").append(describeBranch(branch)).append(" → ").append(titleOrId(titles, branch.goTo())).append('\n');
            described.add(branch.goTo());
        }
        Map<String, String> conditions = stage.transitionConditions() == null ? Map.of() : stage.transitionConditions();
        conditions.forEach((target, condition) -> {
            prompt.append("- ").append(condition.strip()).append(" → ").append(titleOrId(titles, target)).append('\n');
            described.add(target);
        });
        List<String> rest = (stage.allowedTransitions() == null ? List.<String>of() : stage.allowedTransitions()).stream()
                .filter(target -> !described.contains(target))
                .toList();
        if (!rest.isEmpty()) {
            prompt.append("- Keyingi: ")
                    .append(String.join(" yoki ", rest.stream().map(target -> titleOrId(titles, target)).toList()))
                    .append('\n');
        }
    }

    private static String describeBranch(StageBranch branch) {
        BranchOperator operator = branch.operator() == null ? BranchOperator.ALWAYS : branch.operator();
        return switch (operator) {
            case ALWAYS -> "Aks holda";
            case EXISTS -> "Agar " + branch.fact() + " ma'lum bo'lsa";
            case MISSING -> "Agar " + branch.fact() + " noma'lum bo'lsa";
            default -> "Agar " + branch.fact() + " " + OPERATOR_SIGNS.get(operator) + " " + branch.value();
        };
    }

    /** The start stage, then breadth-first along every edge; stages no edge reaches follow in list order. */
    private static List<StageDef> orderStages(ScenarioDefinition definition) {
        Map<String, StageDef> byId = new LinkedHashMap<>();
        for (StageDef stage : definition.stages() == null ? List.<StageDef>of() : definition.stages()) {
            if (stage.type() != StageType.NOTE && stage.id() != null) {
                byId.put(stage.id(), stage);
            }
        }
        if (byId.isEmpty()) {
            return List.of();
        }
        String start = definition.startStageId() != null && byId.containsKey(definition.startStageId())
                ? definition.startStageId() : byId.keySet().iterator().next();
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>(List.of(start));
        while (!queue.isEmpty()) {
            String id = queue.poll();
            StageDef stage = byId.get(id);
            if (stage == null || !seen.add(id)) {
                continue;
            }
            List<String> targets = new ArrayList<>();
            if (stage.branches() != null) {
                stage.branches().forEach(branch -> targets.add(branch.goTo()));
            }
            if (stage.allowedTransitions() != null) {
                targets.addAll(stage.allowedTransitions());
            }
            if (stage.transitionConditions() != null) {
                targets.addAll(stage.transitionConditions().keySet());
            }
            targets.stream().filter(byId::containsKey).forEach(queue::add);
        }
        seen.addAll(byId.keySet());
        return seen.stream().map(byId::get).toList();
    }

    private static String titleOf(StageDef stage) {
        return isBlank(stage.title()) ? stage.id() : stage.title().strip();
    }

    private static String titleOrId(Map<String, String> titles, String id) {
        return "\"" + titles.getOrDefault(id, id) + "\"";
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
