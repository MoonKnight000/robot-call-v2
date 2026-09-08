package uz.murodjon.robotcallv2.crm.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Uysot Open API v1 settings. Bound from {@code voice-agent.crm.*} (Stage 9,
 * MASTER_ROADMAP.md §11).
 *
 * <p>The paths are configurable rather than compiled in because prod and dev are the same
 * API behind different hosts, and because a path is the one part of this integration that
 * can be repaired without a release.
 *
 * @param callHistoryEmployeeId the Uysot employee every AI call is recorded under.
 *                              {@code POST /v1/open-api/call-history} requires an
 *                              {@code employeeId} and it has to name a real employee, so
 *                              call history stays off until this is set.
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
        Integer callHistoryEmployeeId
) {
}
