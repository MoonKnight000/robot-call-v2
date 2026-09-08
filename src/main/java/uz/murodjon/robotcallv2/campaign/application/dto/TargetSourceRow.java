package uz.murodjon.robotcallv2.campaign.application.dto;

import uz.murodjon.robotcallv2.campaign.domain.entity.TargetSource;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetSourceStep;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetSourceMethod;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetSourceProvider;

import java.time.Instant;
import java.util.List;

/**
 * A campaign's target source as the API returns it — the same fields the request takes,
 * minus {@code authHeaderValue}: a secret this system was given is never handed back out,
 * only replaced.
 *
 * <p>Which fields are filled depends on {@code provider}: GENERIC uses the single-request
 * ones and no steps, CHAINED the reverse. UYSOT_DEBTORS fills neither — it is provisioned
 * rather than configured, and reads where to call from the platform's CRM settings.
 */
public record TargetSourceRow(
        long campaignId,
        TargetSourceProvider provider,
        String url,
        TargetSourceMethod method,
        String requestBody,
        String authHeaderName,
        boolean authHeaderSet,
        String itemsPath,
        String phoneField,
        String clientIdField,
        String languageField,
        boolean replaceTargets,
        boolean syncOnRecurrence,
        boolean enabled,
        List<TargetSourceStep> steps,
        Instant lastSyncAt,
        Integer lastSyncAdded,
        String lastSyncError) {

    public static TargetSourceRow of(TargetSource source) {
        return new TargetSourceRow(
                source.campaignId(), source.providerOrDefault(), source.url(), source.methodOrDefault(),
                source.requestBody(),
                source.authHeaderName(),
                source.authHeaderValue() != null && !source.authHeaderValue().isBlank(),
                source.itemsPath(), source.phoneFieldOrDefault(), source.clientIdField(),
                source.languageField(), source.replaceTargets(), source.syncOnRecurrence(),
                source.enabled(), source.steps(), source.lastSyncAt(), source.lastSyncAdded(),
                source.lastSyncError());
    }
}
