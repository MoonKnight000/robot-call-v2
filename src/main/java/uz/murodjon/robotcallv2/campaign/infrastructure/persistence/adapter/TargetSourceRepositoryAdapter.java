package uz.murodjon.robotcallv2.campaign.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.campaign.application.port.output.TargetSourceRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetSource;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.TargetSourceEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository.TargetSourceJpaRepository;

import java.time.Instant;

@Component
public class TargetSourceRepositoryAdapter implements TargetSourceRepository {

    private final TargetSourceJpaRepository jpaRepository;

    public TargetSourceRepositoryAdapter(TargetSourceJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public TargetSource findByCampaignId(long campaignId) {
        return jpaRepository.findById(campaignId)
                .map(TargetSourceRepositoryAdapter::toTargetSource)
                .orElse(null);
    }

    @Override
    @Transactional
    public TargetSource upsert(long campaignId, TargetSource source) {
        TargetSourceEntity entity = jpaRepository.findById(campaignId).orElseGet(() -> {
            TargetSourceEntity fresh = new TargetSourceEntity();
            fresh.setCampaignId(campaignId);
            fresh.setCreatedAt(Instant.now());
            return fresh;
        });
        entity.setUrl(source.url());
        entity.setHttpMethod(source.methodOrDefault());
        entity.setRequestBody(source.requestBody());
        entity.setAuthHeaderName(source.authHeaderName());
        entity.setAuthHeaderValue(source.authHeaderValue());
        entity.setItemsPath(source.itemsPath());
        entity.setPhoneField(source.phoneFieldOrDefault());
        entity.setClientIdField(source.clientIdField());
        entity.setLanguageField(source.languageField());
        entity.setReplaceTargets(source.replaceTargets());
        entity.setSyncOnRecurrence(source.syncOnRecurrence());
        entity.setEnabled(source.enabled());
        return toTargetSource(jpaRepository.save(entity));
    }

    @Override
    @Transactional
    public void delete(long campaignId) {
        jpaRepository.deleteById(campaignId);
    }

    @Override
    @Transactional
    public void recordSync(long campaignId, Instant syncedAt, int added, String error) {
        jpaRepository.findById(campaignId).ifPresent(entity -> {
            entity.setLastSyncAt(syncedAt);
            entity.setLastSyncAdded(added);
            entity.setLastSyncError(error);
            jpaRepository.save(entity);
        });
    }

    private static TargetSource toTargetSource(TargetSourceEntity entity) {
        return new TargetSource(
                entity.getCampaignId(),
                entity.getUrl(),
                entity.getHttpMethod(),
                entity.getRequestBody(),
                entity.getAuthHeaderName(),
                entity.getAuthHeaderValue(),
                entity.getItemsPath(),
                entity.getPhoneField(),
                entity.getClientIdField(),
                entity.getLanguageField(),
                entity.isReplaceTargets(),
                entity.isSyncOnRecurrence(),
                entity.isEnabled(),
                entity.getLastSyncAt(),
                entity.getLastSyncAdded(),
                entity.getLastSyncError());
    }
}
