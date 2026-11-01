package uz.murodjon.robotcallv2.campaign.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Sort;

import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/**
 * @param status only targets in this status; null for every status
 * @param search part of the phone number; digits and {@code +} are read, anything else is ignored
 */
public record TargetFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<TargetTableField, Sort.Direction> orders,
        TargetStatus status,
        @Size(max = 64) String search)
        implements FilterInterface<TargetTableField> {

    public TargetFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(TargetTableField.ID, Sort.Direction.ASC);
        }
    }

    /** A page of every target, with no narrowing. */
    public TargetFilter(Integer page, Integer size, LinkedHashMap<TargetTableField, Sort.Direction> orders) {
        this(page, size, orders, null, null);
    }

    /** The phone-number pattern for a {@code LIKE}: what the search holds of digits and {@code +}, between wildcards. */
    public String phonePattern() {
        String kept = search == null ? "" : search.replaceAll("[^0-9+]", "");
        return "%" + kept + "%";
    }
}
