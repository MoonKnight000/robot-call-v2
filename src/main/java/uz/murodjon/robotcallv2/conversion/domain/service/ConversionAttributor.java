package uz.murodjon.robotcallv2.conversion.domain.service;

import uz.murodjon.robotcallv2.callrecord.domain.entity.AnsweredCall;
import uz.murodjon.robotcallv2.conversion.domain.enums.AttributionModel;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;

/**
 * Which of a company's calls, if any, earned a conversion.
 *
 * <p>Spring-free and stateless: this is the rule a company will dispute when a campaign's
 * numbers look wrong, so it has to be answerable by handing it a list and a timestamp.
 *
 * <p>Only answered calls count, and only ones that happened <em>before</em> the conversion.
 * Both are what stop a campaign taking credit for something it cannot have caused: a
 * number that rang out changed nobody's mind, and neither did a call placed after the
 * customer had already paid.
 */
public final class ConversionAttributor {

    private ConversionAttributor() {
    }

    /**
     * @param candidates every answered call to the number, in any order
     * @return the call that earns it, or null when nothing in the window qualifies
     */
    public static AnsweredCall attribute(Collection<AnsweredCall> candidates, Instant occurredAt,
                                           int windowHours, AttributionModel model) {
        if (candidates == null || candidates.isEmpty() || occurredAt == null || windowHours <= 0) {
            return null;
        }
        Instant windowStart = occurredAt.minusSeconds((long) windowHours * 3600);

        Comparator<AnsweredCall> byAnswerTime = Comparator.comparing(AnsweredCall::answeredAt);
        return candidates.stream()
                .filter(call -> call.answeredAt() != null)
                .filter(call -> !call.answeredAt().isAfter(occurredAt))
                .filter(call -> !call.answeredAt().isBefore(windowStart))
                .max(model == AttributionModel.FIRST_CALL ? byAnswerTime.reversed() : byAnswerTime)
                .orElse(null);
    }
}
