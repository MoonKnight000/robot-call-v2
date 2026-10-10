package uz.murodjon.robotcallv2.siptrunk.domain.entity;

import uz.murodjon.robotcallv2.siptrunk.domain.enums.CallerIdStrategy;

import java.time.Instant;
import java.util.List;

/**
 * A pool of caller IDs used for rotation across campaigns.
 */
public record CallerIdPool(
        long id,
        long companyId,
        String name,
        CallerIdStrategy strategy,
        String description,
        List<CallerIdPoolNumber> numbers,
        Instant createdAt,
        Instant updatedAt
) {

    public CallerIdPool {
        strategy = strategy == null ? CallerIdStrategy.ROUND_ROBIN : strategy;
        numbers = numbers == null ? List.of() : List.copyOf(numbers);
    }
}
