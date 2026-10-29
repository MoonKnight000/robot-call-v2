package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.murodjon.robotcallv2.scenario.domain.entity.OutcomeField;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageAction;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.scenario.domain.entity.ToolDef;
import uz.murodjon.robotcallv2.scenario.domain.entity.ToolParamDef;
import uz.murodjon.robotcallv2.scenario.domain.enums.StageType;
import uz.murodjon.robotcallv2.scenario.domain.enums.TransitionMode;
import uz.murodjon.robotcallv2.scenario.domain.service.StageBranchEvaluator;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What happens when a call enters a flow stage ({@link StageType}), shared by both pipelines
 * because both move the call through {@link DialogTools#transitionTo}.
 *
 * <p>Stages that need no words are run here, in code, the moment they are entered — the
 * branches of a LOGIC_SPLIT, the tones of a PRESS_DIGIT, an SMS, a CODE script, an agent
 * handoff — and the call moves straight on to the next stage. Stages whose job needs the
 * model (calling a tool with arguments taken from the conversation, reading values out of
 * it, saying the closing line) are left for it: {@link #describeTask} is what it is told.
 */
final class StageEntry {

    private static final Logger log = LoggerFactory.getLogger(StageEntry.class);

    /** The tool an EXTRACT_VARIABLE stage gives the model; reserved in {@code ScenarioValidator}. */
    static final String EXTRACT_TOOL_NAME = "extractVariables";

    /** {@code w} in a PRESS_DIGIT stage: the pause an IVR needs between menu levels. */
    static final long DTMF_PAUSE_MS = 500;

    private StageEntry() {
    }

    /**
     * Runs every stage the call passes through from {@code entered} until it reaches one that
     * talks or waits for the model. Bounded by the number of stages, so a circle of stages
     * that pass the call on stops where it closes.
     *
     * @return the stage the call ended up in
     */
    static String enter(DialogOutcomeSink s, String entered, StageScriptRunner scripts) {
        ScenarioDefinition scenario = s.scenario();
        if (scenario == null || scenario.stages() == null) {
            return entered;
        }
        String current = entered;
        boolean passedOn = false;
        for (int hop = 0; hop <= scenario.stages().size(); hop++) {
            StageDef stage = scenario.findStage(current);
            if (stage == null) {
                break;
            }
            // null also after a stage that closed the call or handed it to another agent's flow.
            String next = run(s, stage, scripts, passedOn);
            if (next == null) {
                break;
            }
            s.setState(next);
            log.info("[{}] dialog state -> {} (passed on by {} stage {})",
                    s.channelId(), next, stage.type(), current);
            current = next;
            passedOn = true;
        }
        return s.state();
    }

    /** @return the stage the call moves on to without the model, or null to stay */
    private static String run(DialogOutcomeSink s, StageDef stage, StageScriptRunner scripts, boolean passedOn) {
        StageAction action = stage.action();
        switch (stage.type()) {
            case PRESS_DIGIT -> press(s, action == null ? null : action.digits());
            case SMS -> {
                String text = action == null || action.smsText() == null ? null
                        : PromptTemplateEngine.render(action.smsText(), variables(s));
                if (!s.triggerMidCallSms(text)) {
                    log.warn("[{}] SMS stage {} could not send its message", s.channelId(), stage.id());
                }
            }
            case CODE -> {
                Map<String, Object> results = scripts == null ? Map.of()
                        : scripts.run(s.channelId(), action == null ? null : action.code(), variables(s));
                results.forEach(s::recordOutcome);
                log.info("[{}] CODE stage {} set {}", s.channelId(), stage.id(), results.keySet());
            }
            case AGENT_TRANSFER -> {
                if (action != null && action.targetAgentId() != null && s.handOffToAgent(action.targetAgentId())) {
                    log.info("[{}] AGENT_TRANSFER stage {} handed the call to agent {}",
                            s.channelId(), stage.id(), action.targetAgentId());
                    return null;
                }
                log.warn("[{}] AGENT_TRANSFER stage {} could not hand off to agent {}", s.channelId(), stage.id(),
                        action == null ? null : action.targetAgentId());
                return firstTransition(stage);
            }
            case ENDING, CALL_TRANSFER -> {
                // Entered straight from a conversation, the model has already said the line
                // the prompt gave it for this stage; passed on to, nobody has, and the model
                // is asked to say it and close (describeTask).
                if (!passedOn) {
                    close(s, stage);
                }
                return null;
            }
            case CONVERSATION, LOGIC_SPLIT -> {
                return StageBranchEvaluator.findTarget(stage.branches(), variables(s)::get);
            }
            case FUNCTION, MCP, EXTRACT_VARIABLE, NOTE -> {
                // The job comes first; deciding on entry would skip it.
                return null;
            }
        }
        String branched = StageBranchEvaluator.findTarget(stage.branches(), variables(s)::get);
        return branched != null ? branched : firstTransition(stage);
    }

    /** Ends the call the way an ENDING or CALL_TRANSFER stage does; the line has been said. */
    static void close(DialogOutcomeSink s, StageDef stage) {
        if (stage.type() == StageType.CALL_TRANSFER) {
            s.setTransferReason("stage " + stage.id());
            s.end(Disposition.TRANSFERRED);
        } else {
            // null keeps whatever the call recorded; the finalizer settles the rest.
            s.end(null);
        }
        log.info("[{}] {} stage {} closed the call", s.channelId(), stage.type(), stage.id());
    }

    private static void press(DialogOutcomeSink s, String digits) {
        if (digits == null) {
            return;
        }
        String[] groups = digits.split("w", -1);
        for (int i = 0; i < groups.length; i++) {
            if (i > 0) {
                try {
                    Thread.sleep(DTMF_PAUSE_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (!groups[i].isEmpty() && !s.sendDtmf(groups[i])) {
                log.warn("[{}] PRESS_DIGIT: this call cannot send tones", s.channelId());
                return;
            }
        }
    }

    private static String firstTransition(StageDef stage) {
        return stage.allowedTransitions() == null || stage.allowedTransitions().isEmpty()
                ? null : stage.allowedTransitions().getFirst();
    }

    /**
     * What branches, templates and scripts read: the call's facts, overlaid with what the
     * call has recorded so far — a value read out of the conversation is newer than the data
     * the call was dialled with.
     */
    static Map<String, Object> variables(DialogOutcomeSink s) {
        Map<String, Object> variables = new LinkedHashMap<>();
        if (s.context() != null && s.context().facts() != null) {
            variables.putAll(s.context().facts());
        }
        if (s.outcome() != null) {
            variables.putAll(s.outcome());
        }
        return variables;
    }

    /** Whether the stage's job needs the model: a tool to call, values to read, a closing line to say. */
    static boolean needsModelAction(StageDef stage) {
        return stage != null && switch (stage.type()) {
            case FUNCTION, MCP, EXTRACT_VARIABLE, ENDING, CALL_TRANSFER -> true;
            default -> false;
        };
    }

    /** How many turns the stage's job takes the model: do it, then move on — or only the first for a closing stage. */
    static int countModelSteps(StageDef stage) {
        return stage.type() == StageType.ENDING || stage.type() == StageType.CALL_TRANSFER ? 1 : 2;
    }

    /**
     * What the model is told to do in {@code stage}: {@code step} 1 is the stage's job,
     * step 2 moving on once it is done.
     */
    static String describeTask(ScenarioDefinition scenario, StageDef stage, int step, Map<String, Object> variables) {
        StageAction action = stage.action();
        if (step > 1) {
            return "Stage " + stage.id() + " is done. Move on now to ONE of: "
                    + SystemPromptFactory.describeReachableStages(scenario, stage)
                    + " — choose by what the tool answered or what was recorded; first write that stage's line "
                    + "as text, then call transitionTo for it.";
        }
        return switch (stage.type()) {
            case FUNCTION -> "Stage " + stage.id() + ": call the tool " + (action == null ? "" : action.toolName())
                    + " now, with what the conversation has given. Do not ask the client anything first.";
            case MCP -> "Stage " + stage.id() + ": call the tool whose name ends in \"__"
                    + HttpToolExecutor.sanitizeToolName(action == null ? null : action.toolName())
                    + "\" now, with what the conversation has given. Do not ask the client anything first.";
            case EXTRACT_VARIABLE -> "Stage " + stage.id() + ": call " + EXTRACT_TOOL_NAME
                    + " now with the values the conversation has given; leave out any it has not. "
                    + "Write no text to the client in this step.";
            case ENDING -> closingTask(stage, variables, "a short goodbye", "endCall");
            case CALL_TRANSFER -> closingTask(stage, variables, "that you are connecting them to a colleague",
                    "requestHumanTransfer (reason: " + stage.id() + ")");
            default -> "";
        };
    }

    private static String closingTask(StageDef stage, Map<String, Object> variables, String otherwise, String tool) {
        String line = SystemPromptFactory.renderStaticLine(stage, variables);
        return "Stage " + stage.id() + ": say " + (line != null ? "EXACTLY this: «" + line + "»" : otherwise)
                + ", then call " + tool + ".";
    }

    /**
     * The stages the model may move the call to from {@code stage}: its own transitions first,
     * then every global stage, then — under {@link TransitionMode#FLEX} — every other
     * conversation stage. The stage itself is never among them.
     */
    static List<String> findReachableStages(ScenarioDefinition scenario, StageDef stage) {
        if (scenario == null || stage == null) {
            return List.of();
        }
        Set<String> reachable = new LinkedHashSet<>();
        if (stage.allowedTransitions() != null) {
            reachable.addAll(stage.allowedTransitions());
        }
        if (scenario.stages() != null) {
            for (StageDef other : scenario.stages()) {
                if (other.globalCondition() != null && !other.globalCondition().isBlank()) {
                    reachable.add(other.id());
                }
            }
            if (scenario.transitionMode() == TransitionMode.FLEX) {
                for (StageDef other : scenario.stages()) {
                    if (other.type() == StageType.CONVERSATION) {
                        reachable.add(other.id());
                    }
                }
            }
        }
        reachable.remove(stage.id());
        return new ArrayList<>(reachable);
    }

    /** Whether {@code stageId} is a global stage — reachable from anywhere, past any unmet required outcome. */
    static boolean isGlobal(ScenarioDefinition scenario, String stageId) {
        StageDef stage = scenario == null ? null : scenario.findStage(stageId);
        return stage != null && stage.globalCondition() != null && !stage.globalCondition().isBlank();
    }

    /**
     * The tool an EXTRACT_VARIABLE stage gives the model: one parameter per outcome field it
     * names, each recorded as given ({@link ScenarioToolCallbackFactory}); null for any other stage.
     */
    static ToolDef findExtractionTool(ScenarioDefinition scenario, StageDef stage) {
        if (stage == null || stage.type() != StageType.EXTRACT_VARIABLE || stage.action() == null
                || stage.action().extractFields() == null || scenario.outcomeSchema() == null) {
            return null;
        }
        List<ToolParamDef> params = new ArrayList<>();
        for (OutcomeField field : scenario.outcomeSchema()) {
            if (stage.action().extractFields().contains(field.name())) {
                params.add(new ToolParamDef(field.name(), field.type() == null ? "string" : field.type(), false,
                        field.description()));
            }
        }
        return new ToolDef(EXTRACT_TOOL_NAME, "Records values the conversation has given", params);
    }

    /**
     * What a realtime engine is told about a stage that is not a conversation, after its
     * purpose in the stage list: it holds the whole flow at once, and a stage it cannot
     * talk in has to read as one. Empty for a conversation stage.
     */
    static String describeKind(StageDef stage) {
        return switch (stage.type()) {
            case LOGIC_SPLIT, PRESS_DIGIT, SMS, CODE, AGENT_TRANSFER ->
                    " (runs by itself the moment you move here — the transitionTo result says where the call went)";
            case FUNCTION, MCP, EXTRACT_VARIABLE -> " (after moving here, do what the transitionTo result tells you)";
            case ENDING -> " (closing stage: say its line, then the call ends)";
            case CALL_TRANSFER -> " (closing stage: say its line, then the call is transferred)";
            case CONVERSATION, NOTE -> "";
        };
    }

    /**
     * {@link #findExtractionTool} for a realtime engine, which is handed its tools once at
     * connect: one tool covering the fields of every EXTRACT_VARIABLE stage of the flow, used
     * only while the call is in one ({@code RealtimeToolRunner}); null when the flow has none.
     */
    static ToolDef findExtractionToolForFlow(ScenarioDefinition scenario) {
        if (scenario == null || scenario.stages() == null) {
            return null;
        }
        Set<String> fields = new LinkedHashSet<>();
        for (StageDef stage : scenario.stages()) {
            if (stage.type() == StageType.EXTRACT_VARIABLE && stage.action() != null
                    && stage.action().extractFields() != null) {
                fields.addAll(stage.action().extractFields());
            }
        }
        if (fields.isEmpty()) {
            return null;
        }
        StageDef union = StageDef.builder().id("*").type(StageType.EXTRACT_VARIABLE)
                .action(new StageAction(null, null, null, null, null, null, List.copyOf(fields), null, null))
                .build();
        return findExtractionTool(scenario, union);
    }

    /**
     * Whether the flow sends an SMS of its own (an SMS stage). The call then needs a sender
     * even when its agent does not let the model text the caller on its own.
     */
    static boolean sendsSms(ScenarioDefinition scenario) {
        return scenario != null && scenario.stages() != null
                && scenario.stages().stream().anyMatch(stage -> stage.type() == StageType.SMS);
    }

    /** The number a CALL_TRANSFER stage the call is in hands it to, or null when it is in no such stage. */
    static String findTransferTarget(ScenarioDefinition scenario, String state) {
        StageDef stage = scenario == null ? null : scenario.findStage(state);
        return stage != null && stage.type() == StageType.CALL_TRANSFER && stage.action() != null
                ? stage.action().transferTo() : null;
    }
}
