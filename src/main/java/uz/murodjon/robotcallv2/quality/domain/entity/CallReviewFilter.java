package uz.murodjon.robotcallv2.quality.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.quality.domain.enums.CallReviewStatus;
import uz.murodjon.robotcallv2.quality.domain.enums.CallReviewTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.time.Instant;
import java.util.LinkedHashMap;

/**
 * The review inbox's page.
 *
 * @param status null — every call that was flagged or reviewed; set — only those in that state
 * @param from   inclusive, by when the call was scored
 * @param to     exclusive
 */
public record CallReviewFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<CallReviewTableField, Sort.Direction> orders,
        CallReviewStatus status,
        Long aiAgentId,
        Instant from,
        Instant to
) implements FilterInterface<CallReviewTableField> {

    public CallReviewFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(CallReviewTableField.CREATED_AT, Sort.Direction.DESC);
        }
    }
}
