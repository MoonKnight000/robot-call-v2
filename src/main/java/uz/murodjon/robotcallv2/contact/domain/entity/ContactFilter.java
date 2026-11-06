package uz.murodjon.robotcallv2.contact.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;

import uz.murodjon.robotcallv2.contact.domain.enums.ContactTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pagination, sort and filters for POST /api/contacts/list (§10.8). Every filter left
 * {@code null}/empty does not narrow; the ones given are combined with AND.
 *
 * @param search       free-text match against name, phone or any custom field value
 * @param tags         contacts carrying at least one of these tags
 * @param customFields contacts whose custom field equals the given value, ignoring case, for every entry
 * @param lastCallFrom contacts whose latest call started at or after this instant
 * @param lastCallTo   contacts whose latest call started at or before this instant
 */
public record ContactFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<ContactTableField, Sort.Direction> orders,
        String search,
        List<String> tags,
        Map<String, String> customFields,
        Instant lastCallFrom,
        Instant lastCallTo)
        implements FilterInterface<ContactTableField> {

    public ContactFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(ContactTableField.NAME, Sort.Direction.ASC);
        }
        tags = tags == null ? List.of() : tags.stream().filter(t -> t != null && !t.isBlank()).map(String::strip).toList();
        Map<String, String> given = new LinkedHashMap<>();
        if (customFields != null) {
            customFields.forEach((key, value) -> {
                if (key != null && value != null) {
                    given.put(key, value);
                }
            });
        }
        customFields = Collections.unmodifiableMap(given);
    }

    /** Paging, sort and free-text search only. */
    public ContactFilter(Integer page, Integer size, LinkedHashMap<ContactTableField, Sort.Direction> orders,
                         String search) {
        this(page, size, orders, search, null, null, null, null);
    }
}
