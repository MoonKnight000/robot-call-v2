package uz.murodjon.robotcallv2.crm.application.dto;

import jakarta.validation.constraints.Positive;

import java.util.HashMap;
import java.util.Map;

/**
 * PUT /api/settings/crm — every field optional; {@code null} clears it back to the platform default.
 * Supports both legacy flat status fields and dynamic {@code statusMappings}.
 */
public record UpdateCrmSettingsRequest(
        @Positive Integer aiEmployeeId,
        @Positive Integer meetingBookedStatusId,
        @Positive Integer promiseToPayStatusId,
        @Positive Integer notInterestedStatusId,
        @Positive Integer callbackStatusId,
        @Positive Integer newLeadStatusId,
        @Positive Integer taskTypeId,
        Map<String, @Positive Integer> statusMappings
) {

    public UpdateCrmSettingsRequest(Integer aiEmployeeId, Integer meetingBookedStatusId, Integer promiseToPayStatusId,
                                    Integer notInterestedStatusId, Integer callbackStatusId, Integer newLeadStatusId,
                                    Integer taskTypeId) {
        this(aiEmployeeId, meetingBookedStatusId, promiseToPayStatusId, notInterestedStatusId, callbackStatusId,
                newLeadStatusId, taskTypeId, null);
    }

    public Map<String, Integer> resolveStatusMappings() {
        Map<String, Integer> map = new HashMap<>();
        if (statusMappings != null) {
            map.putAll(statusMappings);
        }
        if (meetingBookedStatusId != null) {
            map.put("MEETING_BOOKED", meetingBookedStatusId);
        }
        if (promiseToPayStatusId != null) {
            map.put("PROMISE_TO_PAY", promiseToPayStatusId);
        }
        if (notInterestedStatusId != null) {
            map.put("NOT_INTERESTED", notInterestedStatusId);
        }
        if (callbackStatusId != null) {
            map.put("CALLBACK_REQUESTED", callbackStatusId);
        }
        if (newLeadStatusId != null) {
            map.put("NEW_LEAD", newLeadStatusId);
        }
        return map;
    }
}
