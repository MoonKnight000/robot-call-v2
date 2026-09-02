package uz.murodjon.robotcallv2.donotcall.application.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallTableField;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Request body for {@code POST /api/do-not-call/list} (§10.8). */
public record DoNotCallFilter(
        Integer page,
        Integer size,
        Map<DoNotCallTableField, Sort.Direction> orders
) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;

    public DoNotCallFilter {
        if (orders == null || orders.isEmpty()) {
            LinkedHashMap<DoNotCallTableField, Sort.Direction> defaultOrders = new LinkedHashMap<>();
            defaultOrders.put(DoNotCallTableField.CREATED_AT, Sort.Direction.DESC);
            defaultOrders.put(DoNotCallTableField.PHONE, Sort.Direction.ASC);
            orders = defaultOrders;
        } else if (!orders.containsKey(DoNotCallTableField.PHONE)) {
            LinkedHashMap<DoNotCallTableField, Sort.Direction> deterministicOrders = new LinkedHashMap<>(orders);
            deterministicOrders.put(DoNotCallTableField.PHONE, Sort.Direction.ASC);
            orders = deterministicOrders;
        }
    }

    public int pageOrDefault() {
        return page != null && page >= 0 ? page : DEFAULT_PAGE;
    }

    public int sizeOrDefault() {
        return size != null && size > 0 ? size : DEFAULT_SIZE;
    }

    public Pageable pageable() {
        return PageRequest.of(pageOrDefault(), sizeOrDefault(), sort());
    }

    public Sort sort() {
        List<Sort.Order> list = new ArrayList<>();
        orders.forEach((field, dir) -> {
            String property = switch (field) {
                case PHONE -> "phone";
                case REASON -> "reason";
                case SOURCE -> "source";
                case CREATED_AT -> "createdAt";
            };
            list.add(new Sort.Order(dir, property));
        });
        return Sort.by(list);
    }
}
