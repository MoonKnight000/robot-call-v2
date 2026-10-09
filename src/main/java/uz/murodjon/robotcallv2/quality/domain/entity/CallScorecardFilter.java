package uz.murodjon.robotcallv2.quality.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.quality.domain.enums.CallScorecardTableField;
import uz.murodjon.robotcallv2.quality.domain.enums.ScorecardReviewStatus;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.time.Instant;
import java.util.LinkedHashMap;

/**
 * The operator call scorecards' page — the review inbox when narrowed to {@code PENDING}.
 *
 * @param status         null — every scored call; set — only those in that review state
 * @param operatorUserId only this operator's calls
 * @param from           inclusive, by when the call was scored
 * @param to             exclusive
 */
public record CallScorecardFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<CallScorecardTableField, Sort.Direction> orders,
        ScorecardReviewStatus status,
        Long operatorUserId,
        Instant from,
        Instant to
) implements FilterInterface<CallScorecardTableField> {

    public CallScorecardFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(CallScorecardTableField.CREATED_AT, Sort.Direction.DESC);
        }
    }
}
