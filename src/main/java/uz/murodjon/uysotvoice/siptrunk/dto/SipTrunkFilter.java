package uz.murodjon.uysotvoice.siptrunk.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;

import uz.murodjon.uysotvoice.shared.api.FilterInterface;
import uz.murodjon.uysotvoice.siptrunk.enums.SipTrunkTableField;

import java.util.LinkedHashMap;

/** Pagination + sort for {@code POST /api/sip-trunks/list} (ROADMAP B.3). */
public record SipTrunkFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<SipTrunkTableField, Sort.Direction> orders)
        implements FilterInterface<SipTrunkTableField> {

    public SipTrunkFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(SipTrunkTableField.NAME, Sort.Direction.ASC);
        }
    }
}
