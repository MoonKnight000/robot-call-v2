package uz.murodjon.robotcallv2.tool.domain.entity;

import java.util.Map;

public record ToolFilter(
        String search,
        Integer page,
        Integer size,
        Map<String, String> orders
) {
    public ToolFilter {
        page = (page == null || page < 0) ? 0 : page;
        size = (size == null || size <= 0) ? 20 : size;
        orders = orders == null ? Map.of() : Map.copyOf(orders);
    }
}
