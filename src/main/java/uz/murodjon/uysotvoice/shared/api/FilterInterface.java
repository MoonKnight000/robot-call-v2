package uz.murodjon.uysotvoice.shared.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Common shape for every filter/search request (project API standard): a page, a page
 * size, and an ordered multi-column sort. {@code F} is the enum of columns the concrete
 * filter accepts, so a caller can only ever request a sort by a column the entity
 * actually exposes.
 */
public interface FilterInterface<F extends TableField> {

    int DEFAULT_PAGE = 0;
    int DEFAULT_SIZE = 20;
    int MAX_SIZE = 500;

    Integer page();

    Integer size();

    LinkedHashMap<F, Sort.Direction> orders();

    default int pageOrDefault() {
        return page() == null || page() < 0 ? DEFAULT_PAGE : page();
    }

    default int sizeOrDefault() {
        return size() == null || size() <= 0 ? DEFAULT_SIZE : Math.min(size(), MAX_SIZE);
    }

    /**
     * {@code long} rather than {@code int}: {@link #pageOrDefault()} is not upper-bounded,
     * so a client-supplied page number multiplied by the page size can overflow a 32-bit
     * int and wrap into a negative SQL {@code OFFSET}.
     */
    default long offset() {
        return (long) pageOrDefault() * sizeOrDefault();
    }

    /** {@link #pageOrDefault()}/{@link #sizeOrDefault()}/{@link #orders()} as a Spring Data {@link Pageable}. */
    default Pageable pageable() {
        return PageRequest.of(pageOrDefault(), sizeOrDefault(), sort());
    }

    /** {@link #orders()} as a Spring Data {@link Sort}, or unsorted when there are none. */
    default Sort sort() {
        if (orders() == null || orders().isEmpty()) {
            return Sort.unsorted();
        }
        List<Sort.Order> sortOrders = new ArrayList<>();
        for (Map.Entry<F, Sort.Direction> entry : orders().entrySet()) {
            sortOrders.add(new Sort.Order(entry.getValue(), entry.getKey().property()));
        }
        return Sort.by(sortOrders);
    }

    /** An {@code ORDER BY} clause built from {@link #orders()}, or "" when unsorted. */
    default String orderByClause() {
        if (orders() == null || orders().isEmpty()) {
            return "";
        }
        StringBuilder sql = new StringBuilder(" ORDER BY ");
        boolean first = true;
        for (Map.Entry<F, Sort.Direction> entry : orders().entrySet()) {
            if (!first) {
                sql.append(", ");
            }
            sql.append(entry.getKey().column()).append(' ')
                    .append(entry.getValue() == Sort.Direction.DESC ? "DESC" : "ASC");
            first = false;
        }
        return sql.toString();
    }
}
