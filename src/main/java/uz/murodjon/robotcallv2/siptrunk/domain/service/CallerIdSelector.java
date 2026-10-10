package uz.murodjon.robotcallv2.siptrunk.domain.service;

import uz.murodjon.robotcallv2.siptrunk.domain.entity.CallerIdPoolNumber;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.CallerIdStrategy;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pure strategy selector for Caller ID pool numbers.
 */
public final class CallerIdSelector {

    private CallerIdSelector() {
    }

    /**
     * Chooses the best caller ID number from the list of numbers according to the strategy.
     *
     * @param numbers available pool numbers
     * @param strategy rotation strategy
     * @param now current time to evaluate cooling_until
     * @return the selected number, or empty if no eligible number is available
     */
    public static Optional<CallerIdPoolNumber> select(List<CallerIdPoolNumber> numbers,
                                                      CallerIdStrategy strategy,
                                                      Instant now) {
        if (numbers == null || numbers.isEmpty()) {
            return Optional.empty();
        }

        List<CallerIdPoolNumber> eligible = numbers.stream()
                .filter(n -> n.isEligible(now))
                .toList();

        if (eligible.isEmpty()) {
            return Optional.empty();
        }

        CallerIdStrategy effectiveStrategy = strategy != null ? strategy : CallerIdStrategy.ROUND_ROBIN;

        return switch (effectiveStrategy) {
            case RANDOM -> Optional.of(eligible.get(ThreadLocalRandom.current().nextInt(eligible.size())));
            case LEAST_USED -> eligible.stream()
                    .min(Comparator.comparingLong(CallerIdPoolNumber::usageCount)
                            .thenComparing(n -> n.lastUsedAt() == null ? Instant.EPOCH : n.lastUsedAt()));
            case HIGHEST_ANSWER_RATE -> eligible.stream()
                    .max(Comparator.comparingDouble(CallerIdPoolNumber::answerRate)
                            .thenComparing(Comparator.comparingLong(CallerIdPoolNumber::usageCount).reversed()));
            case ROUND_ROBIN -> eligible.stream()
                    .min(Comparator.comparing(n -> n.lastUsedAt() == null ? Instant.EPOCH : n.lastUsedAt()));
        };
    }
}
