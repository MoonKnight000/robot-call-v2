package uz.murodjon.robotcallv2.knowledgebase.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.util.LinkedHashMap;

public record KnowledgeItemFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<KnowledgeTableField, Sort.Direction> orders,
        Long agentId,
        String search
) implements FilterInterface<KnowledgeTableField> {

    public KnowledgeItemFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(KnowledgeTableField.CREATED_AT, Sort.Direction.DESC);
        }
    }
}
