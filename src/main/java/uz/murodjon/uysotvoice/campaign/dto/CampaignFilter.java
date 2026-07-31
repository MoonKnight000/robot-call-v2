package uz.murodjon.uysotvoice.campaign.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;

import uz.murodjon.uysotvoice.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/**
 * Pagination + sort for {@code POST /api/campaigns/list}. {@code page}/{@code size} may be
 * omitted (defaulted by {@link FilterInterface}), but a value that is present and out of
 * range is rejected rather than silently clamped — that is more likely a client bug than a
 * deliberate choice.
 */
public record CampaignFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<CampaignTableField, Sort.Direction> orders)
        implements FilterInterface<CampaignTableField> {

    public CampaignFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(CampaignTableField.ID, Sort.Direction.ASC);
        }
    }
}
