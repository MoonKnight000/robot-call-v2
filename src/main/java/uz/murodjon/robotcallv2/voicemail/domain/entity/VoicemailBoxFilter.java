package uz.murodjon.robotcallv2.voicemail.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;
import uz.murodjon.robotcallv2.voicemail.domain.enums.VoicemailBoxTableField;

import java.util.LinkedHashMap;

/** Pagination + sort for POST /api/voicemail-boxes/list. */
public record VoicemailBoxFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<VoicemailBoxTableField, Sort.Direction> orders
) implements FilterInterface<VoicemailBoxTableField> {

    public VoicemailBoxFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(VoicemailBoxTableField.NAME, Sort.Direction.ASC);
        }
    }
}
