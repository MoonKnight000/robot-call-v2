package uz.murodjon.robotcallv2.report.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;

import uz.murodjon.robotcallv2.report.domain.enums.CallTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Pagination + sort + filter for the call report list ({@code POST /api/reports/calls/list}...).
 * {@code page}/{@code size} may be omitted (defaulted by {@link FilterInterface}), but a
 * value that is present and out of range is rejected rather than silently clamped.
 *
 * @param q               free-text match against the dialled phone number
 * @param campaignId      restrict to one campaign
 * @param disposition     restrict to one final outcome
 * @param dateFrom        only calls started at or after this instant
 * @param dateTo          only calls started before this instant
 * @param durationMinSec  only calls at least this long
 * @param durationMaxSec  only calls at most this long
 * @param ids             when present, {@code POST /api/reports/calls/export} returns exactly
 *                        these calls and every other filter field is ignored — this is how a
 *                        user-selected subset of rows gets exported instead of the whole filter
 */
public record CallFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<CallTableField, Sort.Direction> orders,
        String q,
        Long campaignId,
        Disposition disposition,
        Instant dateFrom,
        Instant dateTo,
        Integer durationMinSec,
        Integer durationMaxSec,
        List<Long> ids)
        implements FilterInterface<CallTableField> {

    /** Newest first by default, matching what a "what just happened" view needs. */
    public CallFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(CallTableField.CALL_ID, Sort.Direction.DESC);
        }
    }
}

