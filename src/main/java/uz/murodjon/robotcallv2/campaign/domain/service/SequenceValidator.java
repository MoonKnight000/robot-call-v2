package uz.murodjon.robotcallv2.campaign.domain.service;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignSequence;
import uz.murodjon.robotcallv2.campaign.domain.entity.ConversationStepConfig;
import uz.murodjon.robotcallv2.campaign.domain.entity.SequenceStep;
import uz.murodjon.robotcallv2.campaign.domain.enums.SequenceOutcome;
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
        if (step.type() != SequenceStepType.CONVERSATION && !step.routes().isEmpty()) {
            errors.add(path + ".routes: only a CONVERSATION step routes on outcomes; use next");
        }
        if (step.type() == SequenceStepType.CONVERSATION && step.next() != null) {
            errors.add(path + ".next: a CONVERSATION step routes on outcomes; use routes");
        }
        requireExisting(path + ".next", step.next(), stepsById, errors);
    }

    private void validateConversation(String path, SequenceStep step, Map<String, SequenceStep> stepsById,
                                      List<String> errors) {
        ConversationStepConfig conversation = step.conversation();
        // The agent may still be unset: a draft sequence is saved before it can be dialled.
        if (conversation != null && conversation.maxAttempts() > ConversationStepConfig.MAX_ATTEMPTS) {
            errors.add(path + ".conversation.maxAttempts: at most " + ConversationStepConfig.MAX_ATTEMPTS);
        }
        step.routes().forEach((outcome, target) -> {
            String routePath = path + ".routes." + outcome;
            // Somebody who asked not to be called again must not be handed to any further step.
            if (outcome == SequenceOutcome.DO_NOT_CALL) {
                errors.add(routePath + ": a contact who asked not to be called ends the sequence");
            }
            requireExisting(routePath, target, stepsById, errors);
        });
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
        List<String> targets = new ArrayList<>(step.routes().values());
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
}
