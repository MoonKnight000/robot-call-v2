package uz.murodjon.robotcallv2.campaign.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import uz.murodjon.robotcallv2.campaign.domain.enums.TargetSourceMethod;

/**
 * Configures where a campaign fetches its call list from
 * ({@code PUT /api/campaigns/{id}/target-source}).
 *
 * @param url             http(s) endpoint. Screened against loopback and private addresses
 *                        at fetch time — this URL is written by a tenant and the request is
 *                        made by this server, from inside its own network
 * @param authHeaderValue write-only. Omit on a later edit to keep the stored secret
 * @param itemsPath       dot path to the array of rows ({@code "data.items"}); omit when
 *                        the response body is the array itself
 * @param phoneField      key holding the number in each row; defaults to {@code phone}
 * @param clientIdField   key holding the CRM client id; omit when the rows carry none
 * @param languageField   key holding a per-row language; omit to use the agent's
 * @param replaceTargets  clear the campaign's targets before importing — for a source that
 *                        answers with the whole of today's list
 * @param syncOnRecurrence fetch automatically on every recurrence run (default true)
 */
public record UpdateTargetSourceRequest(
        @NotBlank @Size(max = 1000) String url,
        TargetSourceMethod method,
        String requestBody,
        @Size(max = 100) String authHeaderName,
        String authHeaderValue,
        @Size(max = 200) String itemsPath,
        @Size(max = 100) String phoneField,
        @Size(max = 100) String clientIdField,
        @Size(max = 100) String languageField,
        Boolean replaceTargets,
        Boolean syncOnRecurrence,
        Boolean enabled) {
}
