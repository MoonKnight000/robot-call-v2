package uz.murodjon.robotcallv2.campaign.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;

import uz.murodjon.robotcallv2.campaign.domain.enums.TargetTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.util.LinkedHashMap;

public record TargetFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<TargetTableField, Sort.Direction> orders)
        implements FilterInterface<TargetTableField> {

    public TargetFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(TargetTableField.ID, Sort.Direction.ASC);
        }
    }
}
