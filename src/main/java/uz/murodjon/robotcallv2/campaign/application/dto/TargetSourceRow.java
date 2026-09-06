package uz.murodjon.robotcallv2.campaign.application.dto;

import uz.murodjon.robotcallv2.campaign.domain.entity.TargetSource;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetSourceMethod;

import java.time.Instant;

/**
 * A campaign's target source as the API returns it — the same fields the request takes,
 * minus {@code authHeaderValue}: a secret this system was given is never handed back out,
 * only replaced.
 */
public record TargetSourceRow(
        long campaignId,
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
        Instant lastSyncAt,
        Integer lastSyncAdded,
        String lastSyncError) {

    public static TargetSourceRow of(TargetSource source) {
        return new TargetSourceRow(
                source.campaignId(), source.url(), source.methodOrDefault(), source.requestBody(),
                source.authHeaderName(),
                source.authHeaderValue() != null && !source.authHeaderValue().isBlank(),
                source.itemsPath(), source.phoneFieldOrDefault(), source.clientIdField(),
                source.languageField(), source.replaceTargets(), source.syncOnRecurrence(),
                source.enabled(), source.lastSyncAt(), source.lastSyncAdded(), source.lastSyncError());
    }
}
