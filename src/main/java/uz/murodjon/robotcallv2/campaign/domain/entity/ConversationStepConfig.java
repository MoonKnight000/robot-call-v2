package uz.murodjon.robotcallv2.campaign.domain.entity;

import uz.murodjon.robotcallv2.campaign.domain.enums.DelayUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The calls a CONVERSATION step places.
 *
 * @param aiAgentId the agent that speaks on this step's calls
 * @param attempts  the step's calls in order, each with its delay after the previous one; when they
 *                  are spent unanswered the contact follows the step's No Response route
 */
public record ConversationStepConfig(Long aiAgentId, List<StepAttempt> attempts) {

    /** Retell's default: immediately, then 3, 7 and 10 days after the previous attempt — days 0, 3, 10, 20. */
    public static final List<StepAttempt> DEFAULT_ATTEMPTS = List.of(
            StepAttempt.createImmediate(),
            new StepAttempt(3, DelayUnit.DAYS),
            new StepAttempt(7, DelayUnit.DAYS),
            new StepAttempt(10, DelayUnit.DAYS));
    public static final int MAX_ATTEMPTS = 50;

    public ConversationStepConfig {
        List<StepAttempt> normalized = new ArrayList<>(attempts == null ? List.of() : attempts);
        normalized.removeIf(Objects::isNull);
        // The first call goes out when the contact reaches the step; a delay before it is the outcome's.
        if (!normalized.isEmpty()) {
            normalized.set(0, StepAttempt.createImmediate());
        }
        attempts = List.copyOf(normalized);
    }

    public int countAttempts() {
        return attempts.size();
    }
}
