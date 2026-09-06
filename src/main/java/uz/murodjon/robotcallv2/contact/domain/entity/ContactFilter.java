package uz.murodjon.robotcallv2.contact.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;

import uz.murodjon.robotcallv2.contact.domain.enums.ContactTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/**
 * Pagination + sort for POST /api/contacts/list (§10.8).
 *
 * @param search free-text match against name or phone, or null/blank for no filter
 */
public record ContactFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<ContactTableField, Sort.Direction> orders,
        String search)
        implements FilterInterface<ContactTableField> {

    public ContactFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(ContactTableField.NAME, Sort.Direction.ASC);
        }
    }
}
