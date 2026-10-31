package uz.murodjon.robotcallv2.campaign.domain.service;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignSequence;
import uz.murodjon.robotcallv2.campaign.domain.entity.ConversationStepConfig;
import uz.murodjon.robotcallv2.campaign.domain.entity.SequenceStep;
import uz.murodjon.robotcallv2.campaign.domain.entity.OutcomeRoute;
import uz.murodjon.robotcallv2.campaign.domain.entity.SequenceOutcome;
import uz.murodjon.robotcallv2.campaign.domain.enums.OutcomeEvaluation;
import uz.murodjon.robotcallv2.campaign.domain.enums.OutcomeExit;
import uz.murodjon.robotcallv2.campaign.domain.enums.SequenceStepType;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Whether a campaign sequence can be dialled: every step has what its type needs, every
 * route lands on a step that exists, and no route leads back — a contact could loop through
 * the same steps for ever. A step nothing leads to is allowed; it is a draft node.
 */
@Component
public class SequenceValidator {

    static final int MAX_STEPS = 20;
    /** Same ceiling as a follow-up SMS in a retry rule: three Cyrillic SMS parts. */
    static final int MAX_SMS_LENGTH = 459;
    static final int MAX_WAIT_MINUTES = 30 * 24 * 60;
    static final int MAX_OUTCOMES = 20;
    static final long MAX_ATTEMPT_DELAY_MINUTES = 365L * 24 * 60;
    static final int MAX_PROMPT_LENGTH = 2000;
    static final int MAX_TAG_LENGTH = 50;

    /** Every problem found, raised together; a null sequence has nothing to validate. */
    public void validate(CampaignSequence sequence) {
        if (sequence == null) {
            return;
        }
        List<String> errors = new ArrayList<>();
        Map<String, SequenceStep> stepsById = indexSteps(sequence, errors);
        if (sequence.steps().isEmpty()) {
            errors.add("steps: at least one step is required");
        }
        SequenceStep start = sequence.startStepId() == null ? null : stepsById.get(sequence.startStepId());
        if (start == null) {
            errors.add("startStepId: must name one of the steps");
        } else if (start.type() != SequenceStepType.CONVERSATION) {
            errors.add("startStepId: a contact enters the sequence on a CONVERSATION step");
        }
        for (int index = 0; index < sequence.steps().size(); index++) {
            validateStep("steps[" + index + "]", sequence.steps().get(index), stepsById, errors);
        }
        Set<String> outcomeIds = new HashSet<>();
        validateOutcomes("globalOutcomes", sequence.globalOutcomes(), false, stepsById, outcomeIds, errors);
        for (int index = 0; index < sequence.steps().size(); index++) {
            SequenceStep step = sequence.steps().get(index);
            if (step.type() == SequenceStepType.CONVERSATION) {
                // A step's outcome may share an id with another step's; never with a global one.
                Set<String> stepIds = new HashSet<>(globalIds(sequence));
                validateOutcomes("steps[" + index + "].outcomes.outcomes", step.outcomes().outcomes(), true,
                        stepsById, stepIds, errors);
            }
        }
        if (errors.isEmpty() && hasCycle(stepsById)) {
            errors.add("steps: routes must not lead back to a step already passed");
        }
        if (!errors.isEmpty()) {
            throw ValidationException.withErrors(ErrorCode.CAMPAIGN_SEQUENCE_INVALID, errors, errors.size());
        }
    }

    /**
     * What {@link #validate} leaves open for a draft: a campaign cannot be launched while any
     * conversation step has no agent to speak.
     */
    public void validateRunnable(CampaignSequence sequence) {
        if (sequence == null) {
            return;
        }
        List<String> errors = new ArrayList<>();
        for (int index = 0; index < sequence.steps().size(); index++) {
            SequenceStep step = sequence.steps().get(index);
            if (step.type() == SequenceStepType.CONVERSATION
                    && (step.conversation() == null || step.conversation().aiAgentId() == null)) {
                errors.add("steps[" + index + "].conversation.aiAgentId: required to launch");
            }
        }
        if (!errors.isEmpty()) {
            throw ValidationException.withErrors(ErrorCode.CAMPAIGN_SEQUENCE_INVALID, errors, errors.size());
        }
    }

    private static Map<String, SequenceStep> indexSteps(CampaignSequence sequence, List<String> errors) {
        Map<String, SequenceStep> stepsById = new HashMap<>();
        if (sequence.steps().size() > MAX_STEPS) {
            errors.add("steps: at most " + MAX_STEPS + " steps");
        }
        for (int index = 0; index < sequence.steps().size(); index++) {
            SequenceStep step = sequence.steps().get(index);
            if (step.id() == null || step.id().isBlank()) {
                errors.add("steps[" + index + "].id: required");
            } else if (stepsById.put(step.id(), step) != null) {
                errors.add("steps[" + index + "].id: '" + step.id() + "' is used by another step");
            }
        }
        return stepsById;
    }

    private void validateStep(String path, SequenceStep step, Map<String, SequenceStep> stepsById,
                              List<String> errors) {
        if (step.type() == null) {
            errors.add(path + ".type: required");
            return;
        }
        switch (step.type()) {
            case CONVERSATION -> validateConversation(path, step, stepsById, errors);
            case SMS -> validateSms(path, step, errors);
            case WAIT -> validateWait(path, step, errors);
        }
        if (step.type() != SequenceStepType.CONVERSATION && !step.outcomes().outcomes().isEmpty()) {
            errors.add(path + ".outcomes: only a CONVERSATION step routes on outcomes; use next");
        }
        if (step.type() == SequenceStepType.CONVERSATION && step.next() != null) {
            errors.add(path + ".next: a CONVERSATION step routes on outcomes; use outcomes");
        }
        requireExisting(path + ".next", step.next(), stepsById, errors);
    }

    private void validateConversation(String path, SequenceStep step, Map<String, SequenceStep> stepsById,
                                      List<String> errors) {
        ConversationStepConfig conversation = step.conversation();
        // The agent may still be unset: a draft sequence is saved before it can be dialled.
        if (conversation == null || conversation.attempts().isEmpty()) {
            errors.add(path + ".conversation.attempts: at least one attempt is required");
        } else if (conversation.countAttempts() > ConversationStepConfig.MAX_ATTEMPTS) {
            errors.add(path + ".conversation.attempts: at most " + ConversationStepConfig.MAX_ATTEMPTS);
        } else {
            for (int index = 0; index < conversation.attempts().size(); index++) {
                if (conversation.attempts().get(index).computeDelayMinutes() > MAX_ATTEMPT_DELAY_MINUTES) {
                    errors.add(path + ".conversation.attempts[" + index + "].delay: at most 365 days");
                }
            }
        }
        validateRoute(path + ".outcomes.other", step.outcomes().other(), true, stepsById, errors);
        validateRoute(path + ".outcomes.noResponse", step.outcomes().noResponse(), true, stepsById, errors);
    }

    private static List<String> globalIds(CampaignSequence sequence) {
        return sequence.globalOutcomes().stream().map(SequenceOutcome::id).toList();
    }

    /**
     * @param onStep a step's own outcomes may delay the next step; global ones may not
     * @param ids    the ids already taken; each outcome's id is added
     */
    private static void validateOutcomes(String path, List<SequenceOutcome> outcomes, boolean onStep,
                                         Map<String, SequenceStep> stepsById, Set<String> ids, List<String> errors) {
        if (outcomes.size() > MAX_OUTCOMES) {
            errors.add(path + ": at most " + MAX_OUTCOMES + " outcomes");
        }
        for (int index = 0; index < outcomes.size(); index++) {
            SequenceOutcome outcome = outcomes.get(index);
            String outcomePath = path + "[" + index + "]";
            if (outcome.id() == null || outcome.id().isBlank()) {
                errors.add(outcomePath + ".id: required");
            } else if (!ids.add(outcome.id())) {
                errors.add(outcomePath + ".id: '" + outcome.id() + "' is used by another outcome");
            }
            if (outcome.name() == null || outcome.name().isBlank()) {
                errors.add(outcomePath + ".name: required");
            }
            if (outcome.evaluation() == OutcomeEvaluation.PROMPT) {
                if (outcome.prompt() == null || outcome.prompt().isBlank()) {
                    errors.add(outcomePath + ".prompt: required for a PROMPT outcome");
                } else if (outcome.prompt().length() > MAX_PROMPT_LENGTH) {
                    errors.add(outcomePath + ".prompt: at most " + MAX_PROMPT_LENGTH + " characters");
                }
            } else {
                if (outcome.conditions().isEmpty()) {
                    errors.add(outcomePath + ".conditions: a METRICS outcome needs at least one");
                }
                for (int c = 0; c < outcome.conditions().size(); c++) {
                    var condition = outcome.conditions().get(c);
                    if (condition == null || condition.variable() == null || condition.variable().isBlank()
                            || condition.operator() == null) {
                        errors.add(outcomePath + ".conditions[" + c + "]: variable and operator are required");
                    }
                }
            }
            for (String tag : outcome.saveToContact().tags()) {
                if (tag == null || tag.isBlank() || tag.contains(",") || tag.length() > MAX_TAG_LENGTH) {
                    errors.add(outcomePath + ".saveToContact.tags: a tag is 1–" + MAX_TAG_LENGTH
                            + " characters with no comma");
                }
            }
            // Somebody who asked not to be called again must not be handed to any further step.
            if (outcome.saveToContact().doNotCall() && outcome.next().exit() == OutcomeExit.STEP) {
                errors.add(outcomePath + ".next: an outcome that puts the number on do-not-call ends the sequence");
            }
            validateRoute(outcomePath + ".next", outcome.next(), onStep, stepsById, errors);
        }
    }

    private static void validateRoute(String path, OutcomeRoute route, boolean delayAllowed,
                                      Map<String, SequenceStep> stepsById, List<String> errors) {
        if (route.exit() == OutcomeExit.STEP) {
            if (route.stepId() == null) {
                errors.add(path + ".stepId: required when exit is STEP");
            } else if (!stepsById.containsKey(route.stepId())) {
                errors.add(path + ".stepId: no step '" + route.stepId() + "'");
            }
        } else if (route.stepId() != null) {
            errors.add(path + ".stepId: only an exit to STEP names a step");
        }
        if (route.delayMinutes() > 0 && (!delayAllowed || route.exit() != OutcomeExit.STEP)) {
            errors.add(path + ".delayMinutes: only a step's own outcome that leads to a step may wait");
        } else if (route.delayMinutes() > MAX_WAIT_MINUTES) {
            errors.add(path + ".delayMinutes: at most " + MAX_WAIT_MINUTES);
        }
    }

    private void validateSms(String path, SequenceStep step, List<String> errors) {
        if (step.smsText() == null || step.smsText().isBlank()) {
            errors.add(path + ".smsText: required");
        } else if (step.smsText().length() > MAX_SMS_LENGTH) {
            errors.add(path + ".smsText: at most " + MAX_SMS_LENGTH + " characters");
        }
    }

    private void validateWait(String path, SequenceStep step, List<String> errors) {
        if (step.waitMinutes() == null || step.waitMinutes() < 1 || step.waitMinutes() > MAX_WAIT_MINUTES) {
            errors.add(path + ".waitMinutes: between 1 and " + MAX_WAIT_MINUTES);
        }
    }

    private static void requireExisting(String path, String target, Map<String, SequenceStep> stepsById,
                                        List<String> errors) {
        if (target != null && !stepsById.containsKey(target)) {
            errors.add(path + ": no step '" + target + "'");
        }
    }

    private static boolean hasCycle(Map<String, SequenceStep> stepsById) {
        Set<String> done = new HashSet<>();
        for (String id : stepsById.keySet()) {
            if (leadsBack(id, stepsById, new HashSet<>(), done)) {
                return true;
            }
        }
        return false;
    }

    private static boolean leadsBack(String id, Map<String, SequenceStep> stepsById, Set<String> onPath,
                                     Set<String> done) {
        if (done.contains(id)) {
            return false;
        }
        if (!onPath.add(id)) {
            return true;
        }
        SequenceStep step = stepsById.get(id);
        // A step's own routes only: a global outcome that leads to a step is taken from every step,
        // that step included, and the dialer ends the sequence rather than re-enter the step it is on.
        List<String> targets = new ArrayList<>();
        step.outcomes().outcomes().forEach(outcome -> addStepTarget(outcome.next(), targets));
        addStepTarget(step.outcomes().other(), targets);
        addStepTarget(step.outcomes().noResponse(), targets);
        if (step.next() != null) {
            targets.add(step.next());
        }
        for (String target : targets) {
            if (leadsBack(target, stepsById, onPath, done)) {
                return true;
            }
        }
        onPath.remove(id);
        done.add(id);
        return false;
    }

    private static void addStepTarget(OutcomeRoute route, List<String> targets) {
        if (route.exit() == OutcomeExit.STEP && route.stepId() != null) {
            targets.add(route.stepId());
        }
    }
}
