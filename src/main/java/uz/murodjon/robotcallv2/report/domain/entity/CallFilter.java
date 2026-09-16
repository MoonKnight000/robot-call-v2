package uz.murodjon.robotcallv2.report.domain.entity;

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
 * @param afterId         keyset cursor for the call list: when set, the page holds the calls
 *                        with an id below it, newest first — {@code page} is ignored, an
 *                        {@code orders} other than the default is refused, and no total is
 *                        counted. Pass the last {@code callId} of the previous page. Ignored by
 *                        the export.
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
        List<Long> ids,
        @Min(1) Long afterId)
        implements FilterInterface<CallTableField> {

    /** Newest first by default, matching what a "what just happened" view needs. */
    public CallFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(CallTableField.CALL_ID, Sort.Direction.DESC);
        }
    }

    /** Whether this page is read after a cursor rather than by page number. */
    public boolean hasCursor() {
        return afterId != null;
    }

    /** Whether the page is in the default order, newest call first — the only order a cursor pages in. */
    public boolean hasDefaultOrder() {
        return orders.size() == 1 && orders.get(CallTableField.CALL_ID) == Sort.Direction.DESC;
    }
}

