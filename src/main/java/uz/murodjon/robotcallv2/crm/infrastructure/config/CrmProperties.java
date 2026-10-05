package uz.murodjon.robotcallv2.crm.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Uysot Open API v1 settings. Bound from {@code voice-agent.crm.*} (Stage 9,
 * MASTER_ROADMAP.md §11).
 *
 * <p>The paths are configurable rather than compiled in because prod and dev are the same
 * API behind different hosts, and because a path is the one part of this integration that
 * can be repaired without a release. Prod is {@code api.service.app.uysot.uz}; pointing
 * {@code baseUrl} at the dev host is how a call is tried against test data without writing
 * notes onto real leads.
 *
 * @param apiToken              sent as {@code X-Open-Api-Token} — this API ignores
 *                              {@code Authorization: Bearer}. Blank by default: set it in
 *                              the environment, or connect the company through OAuth
 *                              ({@link
 *                              uz.murodjon.robotcallv2.integration.infrastructure.config.UysotOAuthProperties}),
 *                              which takes precedence over this static token
 * @param leadPath              {@code {id}} is the lead id
 * @param leadNotePath          a POST whose body is an array of 1–10 notes. Like every
 *                              write on this API it answers with a {@code requestId}
 *                              rather than the id of what it created
 * @param contractPath          read by the {@code UYSOT_DEBTORS} target source.
 *                              {@code contract/filter} says which contracts are behind,
 *                              but only this single-contract read carries the lead the
 *                              note is later attached to
 * @param callHistoryEmployeeId the Uysot employee every AI call is recorded under.
 *                              {@code POST /v1/open-api/call-history} requires an
 *                              {@code employeeId} and it has to name a real employee, so
 *                              leaving this unset switches call-history write-back off and
 *                              leaves the lead note — the part that matters — working on
 *                              its own. An id from {@code POST /v1/open-api/employee/filter}
 * @param taskPath              {@code POST /v1/open-api/task} endpoint to schedule tasks/meetings
 * @param leadStatusPath        {@code POST /v1/open-api/lead/{id}/status} endpoint for pipeline stage update
 * @param meetingBookedStatusId CRM status ID to move the lead to when a showroom visit is booked
 * @param notInterestedStatusId CRM status ID to move the lead to when the client is not interested/refuses
 * @param callbackStatusId      CRM status ID when the call could not reach the client (no answer / busy)
 * @param promiseToPayStatusId  CRM status ID when an installment payment promise is made
 */
@ConfigurationProperties(prefix = "voice-agent.crm")
public record CrmProperties(
        boolean enabled,
        String baseUrl,
        String apiToken,
        String leadPath,
        String leadFilterPath,
        String leadNotePath,
        String contractPath,
        String contractFilterPath,
        String callHistoryPath,
        Integer callHistoryEmployeeId,
        String taskPath,
        String leadStatusPath,
        Integer meetingBookedStatusId,
        Integer notInterestedStatusId,
        Integer callbackStatusId,
        Integer promiseToPayStatusId
) {
    @ConstructorBinding
    public CrmProperties {
    }

    public CrmProperties(boolean enabled, String baseUrl, String apiToken, String leadPath,
                         String leadFilterPath, String leadNotePath, String contractPath,
                         String contractFilterPath, String callHistoryPath, Integer callHistoryEmployeeId) {
        this(enabled, baseUrl, apiToken, leadPath, leadFilterPath, leadNotePath, contractPath,
                contractFilterPath, callHistoryPath, callHistoryEmployeeId,
                "/v1/open-api/task", "/v1/open-api/lead/{id}/status", null, null, null, null);
    }

    public CrmProperties(boolean enabled, String baseUrl, String apiToken, String leadPath,
                         String leadFilterPath, String leadNotePath, String contractPath,
                         String contractFilterPath, String callHistoryPath, Integer callHistoryEmployeeId,
                         String taskPath, String leadStatusPath) {
        this(enabled, baseUrl, apiToken, leadPath, leadFilterPath, leadNotePath, contractPath,
                contractFilterPath, callHistoryPath, callHistoryEmployeeId,
                taskPath, leadStatusPath, null, null, null, null);
    }
}
