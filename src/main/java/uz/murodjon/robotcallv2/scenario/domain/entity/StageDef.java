package uz.murodjon.robotcallv2.scenario.domain.entity;

import uz.murodjon.robotcallv2.scenario.domain.enums.StageType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @param model    the LLM this stage runs on instead of the agent's, or null to keep the agent's.
 *                 A greeting or an identity check does not need the model a negotiation does,
 *                 and the difference is paid in cost and time-to-first-token on every turn of it
 * @param branches decisions taken from the call's facts the moment the call enters this
 *                 stage, without the model ({@link StageBranch}); null or empty = none
 * @param transitionConditions when the model takes each of {@code allowedTransitions}, in plain
 *                 words: target stage id → "the client says they have time". Facts can only
 *                 settle what was known before the call ({@code branches}); what the client
 *                 answers is for the model to judge, and a bare list of stage names left it to
 *                 guess which answer leads where. A target with no entry is taken on the
 *                 target's own purpose; null or empty = none. Every key is also an allowed
 *                 transition: a stage may list its next steps here alone, each with its condition,
 *                 and leave {@code allowedTransitions} for the unconditional ones
 * @param staticLine the first thing said on entering this stage, word for word, with {@code {{fact}}}
 *                 templates filled in by the code — the debt notice, a legal sentence: lines
 *                 whose wording is the company's, not the model's to compose. The model is
 *                 handed it ready-made and copies it; null = the model words the stage itself
 * @param requiredOutcomes outcome names of which at least one must be recorded before the call
 *                 may leave this stage — the payment date or the refusal reason. Any of, not
 *                 all of: a client who refuses must still be able to move on. Checked in code
 *                 ({@code DialogTools.transitionTo}), so a model that believes the stage is done
 *                 cannot skip it; null or empty = none
 * @param type     what the stage does ({@link StageType}); null = {@link StageType#CONVERSATION}
 * @param position where the stage sits on the editor's canvas; null = laid out automatically
 * @param globalCondition when set, the stage is global: the model may move the call into it
 *                 from any stage once this holds ("the caller asks for a human"), with no edge
 *                 drawn to it; null = reached only by its incoming transitions
 * @param action   the settings of a non-conversation type ({@link StageAction}); null for
 *                 CONVERSATION, LOGIC_SPLIT, ENDING and NOTE
 */
public record StageDef(
        String id,
        String purpose,
        List<String> allowedTransitions,
        List<String> allowedTools,
        String emotion,
        String model,
        List<StageBranch> branches,
        Map<String, String> transitionConditions,
        String staticLine,
        List<String> requiredOutcomes,
        StageType type,
        StagePosition position,
        String globalCondition,
        StageAction action
) {
    public StageDef {
        if (type == null) {
            type = StageType.CONVERSATION;
        }
        if (transitionConditions != null && !transitionConditions.isEmpty()) {
            Set<String> merged = new LinkedHashSet<>();
            if (allowedTransitions != null) {
                merged.addAll(allowedTransitions);
            }
            merged.addAll(transitionConditions.keySet());
            allowedTransitions = List.copyOf(merged);
        }
    }

    public static StageDefBuilder builder() {
        return new StageDefBuilder();
    }

    /** A builder that starts from this stage, for changing a few of its fields. */
    public StageDefBuilder toBuilder() {
        return new StageDefBuilder(this);
    }

    /**
     * A plain conversation stage: no emotion, model override, branches, conditions, fixed line,
     * required outcomes, canvas position, global condition or action.
     */
    public StageDef(String id, String purpose, List<String> allowedTransitions, List<String> allowedTools) {
        this(id, purpose, allowedTransitions, allowedTools, null, null, null, null, null, null, null, null, null, null);
    }
}
