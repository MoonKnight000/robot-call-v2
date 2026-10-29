package uz.murodjon.robotcallv2.crm.domain.entity;

import java.time.Instant;

/**
 * One company's CRM settings. Every id is the company's own CRM id ({@code null} = not set,
 * the platform's {@code voice-agent.crm.*} fallback applies).
 *
 * @param aiEmployeeId           the CRM employee the AI's call history, tasks and new leads are
 *                               recorded under when no responsible manager is known
 * @param meetingBookedStatusId  pipeline stage a lead moves to when a showroom visit was booked
 * @param promiseToPayStatusId   stage for a debtor who promised to pay
 * @param notInterestedStatusId  stage for a refusal / do-not-call
 * @param callbackStatusId       stage for no answer / callback requested
 * @param newLeadStatusId        stage a lead created from a chat or an inbound call starts in
 */
public record CrmSettings(
        long companyId,
        Integer aiEmployeeId,
        Integer meetingBookedStatusId,
        Integer promiseToPayStatusId,
        Integer notInterestedStatusId,
        Integer callbackStatusId,
        Integer newLeadStatusId,
        Instant updatedAt
) {

    /** What a write carries: the adapter stamps the company and the time. */
    public static CrmSettings values(Integer aiEmployeeId, Integer meetingBookedStatusId, Integer promiseToPayStatusId,
                                     Integer notInterestedStatusId, Integer callbackStatusId, Integer newLeadStatusId) {
        return new CrmSettings(0, aiEmployeeId, meetingBookedStatusId, promiseToPayStatusId, notInterestedStatusId,
                callbackStatusId, newLeadStatusId, null);
    }
}
