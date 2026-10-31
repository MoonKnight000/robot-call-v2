package uz.murodjon.robotcallv2.crm.domain.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * One company's CRM settings. Every id is the company's own CRM id ({@code null} = not set,
 * the platform's {@code voice-agent.crm.*} fallback applies).
 *
 * @param aiEmployeeId   the CRM employee the AI's call history, tasks and new leads are
 *                       recorded under when no responsible manager is known
 * @param taskTypeId     the CRM task type follow-up tasks are created with; {@code null} = the
 *                       CRM's first task type
 * @param statusMappings mapping from disposition / outcome key (e.g. "MEETING_BOOKED", "PROMISE_TO_PAY")
 *                       to the company's CRM pipeline status id
 * @param updatedAt      when these settings were last saved
 */
public record CrmSettings(
        long companyId,
        Integer aiEmployeeId,
        Integer taskTypeId,
        Map<String, Integer> statusMappings,
        Instant updatedAt
) {

    public static final String STATUS_MEETING_BOOKED = "MEETING_BOOKED";
    public static final String STATUS_PROMISE_TO_PAY = "PROMISE_TO_PAY";
    public static final String STATUS_NOT_INTERESTED = "NOT_INTERESTED";
    public static final String STATUS_CALLBACK_REQUESTED = "CALLBACK_REQUESTED";
    public static final String STATUS_NEW_LEAD = "NEW_LEAD";

    public CrmSettings {
        statusMappings = statusMappings != null ? Collections.unmodifiableMap(new HashMap<>(statusMappings)) : Map.of();
    }

    /** What a write carries: the adapter stamps the company and the time. */
    public static CrmSettings values(Integer aiEmployeeId, Integer taskTypeId, Map<String, Integer> statusMappings) {
        return new CrmSettings(0, aiEmployeeId, taskTypeId, statusMappings, null);
    }

    /** Backward-compatible factory for existing callers. */
    public static CrmSettings values(Integer aiEmployeeId, Integer meetingBookedStatusId, Integer promiseToPayStatusId,
                                     Integer notInterestedStatusId, Integer callbackStatusId, Integer newLeadStatusId,
                                     Integer taskTypeId) {
        return new CrmSettings(0, aiEmployeeId, taskTypeId,
                buildMap(meetingBookedStatusId, promiseToPayStatusId, notInterestedStatusId, callbackStatusId, newLeadStatusId),
                null);
    }

    /** Backward-compatible constructor for existing tests and call sites. */
    public CrmSettings(long companyId, Integer aiEmployeeId, Integer meetingBookedStatusId,
                       Integer promiseToPayStatusId, Integer notInterestedStatusId,
                       Integer callbackStatusId, Integer newLeadStatusId, Integer taskTypeId,
                       Instant updatedAt) {
        this(companyId, aiEmployeeId, taskTypeId,
                buildMap(meetingBookedStatusId, promiseToPayStatusId, notInterestedStatusId, callbackStatusId, newLeadStatusId),
                updatedAt);
    }

    private static Map<String, Integer> buildMap(Integer meetingBooked, Integer promiseToPay,
                                                 Integer notInterested, Integer callback, Integer newLead) {
        Map<String, Integer> map = new HashMap<>();
        if (meetingBooked != null) map.put(STATUS_MEETING_BOOKED, meetingBooked);
        if (promiseToPay != null) map.put(STATUS_PROMISE_TO_PAY, promiseToPay);
        if (notInterested != null) map.put(STATUS_NOT_INTERESTED, notInterested);
        if (callback != null) map.put(STATUS_CALLBACK_REQUESTED, callback);
        if (newLead != null) map.put(STATUS_NEW_LEAD, newLead);
        return map;
    }

    public Integer statusFor(String disposition) {
        if (disposition == null || statusMappings == null) {
            return null;
        }
        return statusMappings.get(disposition.toUpperCase());
    }

    public Integer statusFor(Disposition disposition) {
        if (disposition == null) {
            return null;
        }
        Integer direct = statusFor(disposition.name());
        if (direct != null) {
            return direct;
        }
        return switch (disposition) {
            case REFUSED, DO_NOT_CALL -> statusFor(STATUS_NOT_INTERESTED);
            case NO_ANSWER, CALLBACK_REQUESTED, CARRIER_REJECTED -> statusFor(STATUS_CALLBACK_REQUESTED);
            default -> null;
        };
    }

    @JsonProperty("meetingBookedStatusId")
    public Integer meetingBookedStatusId() {
        return statusFor(STATUS_MEETING_BOOKED);
    }

    @JsonProperty("promiseToPayStatusId")
    public Integer promiseToPayStatusId() {
        return statusFor(STATUS_PROMISE_TO_PAY);
    }

    @JsonProperty("notInterestedStatusId")
    public Integer notInterestedStatusId() {
        return statusFor(STATUS_NOT_INTERESTED);
    }

    @JsonProperty("callbackStatusId")
    public Integer callbackStatusId() {
        return statusFor(STATUS_CALLBACK_REQUESTED);
    }

    @JsonProperty("newLeadStatusId")
    public Integer newLeadStatusId() {
        return statusFor(STATUS_NEW_LEAD);
    }
}
