package uz.murodjon.robotcallv2.copilot.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.copilot.domain.enums.CopilotSessionTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/** The user's own copilot conversations, most recently active first. */
public record CopilotSessionFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<CopilotSessionTableField, Sort.Direction> orders
) implements FilterInterface<CopilotSessionTableField> {

    public CopilotSessionFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(CopilotSessionTableField.UPDATED_AT, Sort.Direction.DESC);
        }
    }
}
