package uz.murodjon.robotcallv2.campaign.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.campaign.application.port.output.TargetSourceRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetSource;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetSourceStep;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.TargetSourceEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository.CampaignJpaRepository;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository.TargetSourceJpaRepository;

import java.time.Instant;
import java.util.List;

@Component
public class TargetSourceRepositoryAdapter implements TargetSourceRepository {

    private static final Logger log = LoggerFactory.getLogger(TargetSourceRepositoryAdapter.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<TargetSourceStep>> STEP_LIST_TYPE = new TypeReference<>() {};

    private final TargetSourceJpaRepository jpaRepository;
    private final CampaignJpaRepository campaignJpaRepository;

    public TargetSourceRepositoryAdapter(TargetSourceJpaRepository jpaRepository,
                                         CampaignJpaRepository campaignJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.campaignJpaRepository = campaignJpaRepository;
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
            fresh.setCampaign(campaignJpaRepository.getReferenceById(campaignId));
            fresh.setCreatedAt(Instant.now());
            return fresh;
        });
        entity.setProvider(source.providerOrDefault());
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
        entity.setSteps(writeSteps(source.steps()));
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

    /**
     * Stored steps as the domain sees them. Unreadable JSON reads as no steps rather than
     * throwing: a source whose steps were hand-edited into nonsense must still be
     * retrievable through the API so somebody can fix it, and the sync itself already
     * refuses a chained source with no steps.
     */
    private static List<TargetSourceStep> readSteps(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, STEP_LIST_TYPE);
        } catch (Exception e) {
            log.warn("target source steps are not readable, treating as none: {}", e.getMessage());
            return List.of();
        }
    }

    private static String writeSteps(List<TargetSourceStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(steps);
        } catch (Exception e) {
            log.warn("target source steps could not be stored: {}", e.getMessage());
            return null;
        }
    }

    private static TargetSource toTargetSource(TargetSourceEntity entity) {
        return new TargetSource(
                entity.getCampaignId(),
                entity.getProvider(),
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
                readSteps(entity.getSteps()),
                entity.getLastSyncAt(),
                entity.getLastSyncAdded(),
                entity.getLastSyncError());
    }
}
