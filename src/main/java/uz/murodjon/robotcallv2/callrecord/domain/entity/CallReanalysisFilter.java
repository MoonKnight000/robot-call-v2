package uz.murodjon.robotcallv2.callrecord.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallReanalysisStatus;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallReanalysisTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/** The reanalysis jobs' page; {@code aiAgentId} and {@code status} null mean "any". */
public record CallReanalysisFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<CallReanalysisTableField, Sort.Direction> orders,
        Long aiAgentId,
        CallReanalysisStatus status
) implements FilterInterface<CallReanalysisTableField> {

    public CallReanalysisFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(CallReanalysisTableField.CREATED_AT, Sort.Direction.DESC);
        }
    }
}
