package uz.murodjon.robotcallv2.campaign.application.dto;

import jakarta.validation.constraints.Size;

import uz.murodjon.robotcallv2.campaign.domain.entity.TargetSourceStep;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetSourceMethod;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetSourceProvider;

import java.util.List;

/**
 * Configures where a campaign fetches its call list from
 * ({@code PUT /api/campaigns/{id}/target-source}).
 *
 * <p>Two shapes, chosen by {@code provider}. GENERIC is one request whose answer is already
 * a list of rows with a phone in each, described by the fields below. CHAINED is several
 * requests — {@code steps} — for the common case where the endpoint that knows who to call
 * is not the endpoint that knows their number; the single-request fields are then unused.
 *
 * <p>{@code url} carries no {@code @NotBlank} because a CHAINED source has no single
 * address; {@code CampaignValidator} requires whichever of the two the provider needs, so
 * an omission is still a {@code 400} and not a source that fetches nothing.
 *
 * @param url             http(s) endpoint (GENERIC). Screened against loopback and private
 *                        addresses at fetch time — this URL is written by a tenant and the
 *                        request is made by this server, from inside its own network
 * @param authHeaderValue write-only, and sent on every step of a chain. Omit on a later
 *                        edit to keep the stored secret
 * @param itemsPath       dot path to the array of rows ({@code "data.items"}); omit when
 *                        the response body is the array itself
 * @param phoneField      GENERIC: the key holding the number in each row. CHAINED: the name
 *                        of the variable the steps collected that holds it. Defaults to
 *                        {@code phone} either way
 * @param clientIdField   likewise for the CRM's client id; omit when there is none
 * @param languageField   likewise for a per-row language; omit to use the agent's
 * @param replaceTargets  clear the campaign's targets before importing — for a source that
 *                        answers with the whole of today's list
 * @param syncOnRecurrence fetch automatically on every recurrence run (default true)
 * @param steps           CHAINED only: the requests to run, in order. The first lists rows;
 *                        each later one is called once per row
 */
public record UpdateTargetSourceRequest(
        TargetSourceProvider provider,
        @Size(max = 1000) String url,
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
        Boolean enabled,
        List<TargetSourceStep> steps) {

    /**
     * GENERIC unless asked otherwise, so a client written against the single-request shape
     * keeps working without sending a provider at all.
     */
    public TargetSourceProvider providerOrDefault() {
        return provider != null ? provider : TargetSourceProvider.GENERIC;
    }
}
