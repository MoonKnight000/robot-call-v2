package uz.murodjon.robotcallv2.campaign.domain.entity;

import uz.murodjon.robotcallv2.campaign.domain.enums.TargetSourceMethod;

import java.time.Instant;

/**
 * Where a campaign's call list comes from, when it is not a CSV somebody uploads.
 *
 * <p>The case this exists for is the recurring campaign: DAILY recurrence already re-runs a
 * campaign every morning, but over the same list, so "call today's overdue clients" meant
 * a person exporting a file and uploading it daily. With a source configured, each
 * recurrence run fetches the list from the company's own API first.
 *
 * <p>Field names rather than a mapping language on purpose: the endpoint answers with the
 * company's own JSON and this says which key in each row is the phone, which is the client
 * id and which is the language. Everything else in the row is kept as the target's facts,
 * where the scenario's {@code factSchema} decides what the agent may actually say.
 *
 * @param itemsPath      dot path to the array inside the response ({@code "data.items"});
 *                       null when the body is itself the array
 * @param authHeaderValue the secret in plain text. Encrypted at rest and never returned by
 *                       the API — {@code TargetSourceRow} has no field for it
 * @param replaceTargets clears the campaign's existing targets before importing, for a
 *                       source that answers with the whole of today's list rather than
 *                       what is new since yesterday
 * @param lastSyncError  why the last run fetched nothing, or null when it worked. Kept so
 *                       the failure is visible on the campaign page instead of only in a log
 */
public record TargetSource(
        long campaignId,
        String url,
        TargetSourceMethod method,
        String requestBody,
        String authHeaderName,
        String authHeaderValue,
        String itemsPath,
        String phoneField,
        String clientIdField,
        String languageField,
        boolean replaceTargets,
        boolean syncOnRecurrence,
        boolean enabled,
        Instant lastSyncAt,
        Integer lastSyncAdded,
        String lastSyncError
) {

    public TargetSourceMethod methodOrDefault() {
        return method != null ? method : TargetSourceMethod.GET;
    }

    public String phoneFieldOrDefault() {
        return phoneField != null && !phoneField.isBlank() ? phoneField : "phone";
    }
}
