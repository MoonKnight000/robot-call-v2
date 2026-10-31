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
     * Fewest leading digits a pool number must share with the callee's to count as local: for an
     * Uzbek number, the country code plus the two-digit region or operator code ({@code 99871}).
     */
    static final int MIN_LOCAL_PREFIX_DIGITS = 5;

    /**
     * {@link #select} narrowed to the numbers closest to the callee's own — those sharing the
     * longest prefix with it, provided that prefix is at least {@link #MIN_LOCAL_PREFIX_DIGITS}
     * long. The strategy then picks among the narrowed numbers. With no local number the whole
     * pool is used, so a callee in a region the pool does not cover is still called.
     */
    public static Optional<CallerIdPoolNumber> selectLocal(List<CallerIdPoolNumber> numbers,
                                                           CallerIdStrategy strategy,
                                                           Instant now,
                                                           String calleePhone) {
        if (numbers == null || numbers.isEmpty()) {
            return Optional.empty();
        }
        String callee = digitsOf(calleePhone);
        List<CallerIdPoolNumber> eligible = numbers.stream().filter(n -> n.isEligible(now)).toList();
        int longest = eligible.stream()
                .mapToInt(n -> sharedPrefixLength(callee, digitsOf(n.phone())))
                .max()
                .orElse(0);
        if (longest < MIN_LOCAL_PREFIX_DIGITS) {
            return select(eligible, strategy, now);
        }
        List<CallerIdPoolNumber> closest = eligible.stream()
                .filter(n -> sharedPrefixLength(callee, digitsOf(n.phone())) == longest)
                .toList();
        return select(closest, strategy, now);
    }

    private static String digitsOf(String phone) {
        return phone == null ? "" : phone.replaceAll("\\D", "");
    }

    private static int sharedPrefixLength(String first, String second) {
        int length = Math.min(first.length(), second.length());
        int shared = 0;
        while (shared < length && first.charAt(shared) == second.charAt(shared)) {
            shared++;
        }
        return shared;
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
