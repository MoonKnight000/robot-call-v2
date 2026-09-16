package uz.murodjon.robotcallv2.voicemail.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;
import uz.murodjon.robotcallv2.voicemail.domain.enums.VoicemailMessageTableField;
import uz.murodjon.robotcallv2.voicemail.domain.enums.VoicemailStatus;

import java.util.LinkedHashMap;

/**
 * Pagination + filter for POST /api/voicemails/list.
 *
 * @param status only messages in this status; null for all
 * @param boxId  only this box's messages; null for all
 */
public record VoicemailMessageFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<VoicemailMessageTableField, Sort.Direction> orders,
        VoicemailStatus status,
        Long boxId
) implements FilterInterface<VoicemailMessageTableField> {

    public VoicemailMessageFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(VoicemailMessageTableField.CREATED_AT, Sort.Direction.DESC);
        }
    }
}
