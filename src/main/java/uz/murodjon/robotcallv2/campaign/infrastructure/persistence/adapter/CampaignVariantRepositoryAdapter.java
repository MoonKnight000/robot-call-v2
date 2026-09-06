package uz.murodjon.robotcallv2.campaign.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignVariantRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignVariant;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignVariantEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository.CampaignVariantJpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class CampaignVariantRepositoryAdapter implements CampaignVariantRepository {

    private final CampaignVariantJpaRepository jpaRepository;

    public CampaignVariantRepositoryAdapter(CampaignVariantJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public CampaignVariant save(CampaignVariant variant) {
        CampaignVariantEntity entity;
        if (variant.id() > 0) {
            entity = jpaRepository.findByIdAndCompanyId(variant.id(), variant.companyId())
                    .orElse(new CampaignVariantEntity());
        } else {
            entity = new CampaignVariantEntity();
        }
        entity.setCampaignId(variant.campaignId());
        entity.setCompanyId(variant.companyId());
        entity.setName(variant.name());
        entity.setAiAgentId(variant.aiAgentId());
        entity.setPromptOverride(variant.promptOverride());
        entity.setTtsVoiceId(variant.ttsVoiceId());
        entity.setTrafficWeight(variant.trafficWeight());
        entity.setActive(variant.active());
        entity.setUpdatedAt(Instant.now());

        CampaignVariantEntity saved = jpaRepository.save(entity);
        return toCampaignVariant(saved);
    }

    @Override
    public Optional<CampaignVariant> findByIdAndCompanyId(long id, long companyId) {
        return jpaRepository.findByIdAndCompanyId(id, companyId).map(this::toCampaignVariant);
    }

    @Override
    public List<CampaignVariant> findAllByCampaignIdAndCompanyId(long campaignId, long companyId) {
        return jpaRepository.findAllByCampaignIdAndCompanyId(campaignId, companyId).stream()
                .map(this::toCampaignVariant)
                .toList();
    }

    @Override
    @Transactional
    public void recordCall(long variantId) {
        jpaRepository.incrementCallsCount(variantId);
    }

    @Override
    @Transactional
    public void recordAnswer(long variantId) {
        jpaRepository.incrementAnsweredCount(variantId);
    }

    @Override
    @Transactional
    public void recordConversion(long variantId) {
        jpaRepository.incrementConvertedCount(variantId);
    }

    @Override
    @Transactional
    public void deleteByIdAndCompanyId(long id, long companyId) {
        jpaRepository.deleteByIdAndCompanyId(id, companyId);
    }

    private CampaignVariant toCampaignVariant(CampaignVariantEntity entity) {
        return new CampaignVariant(
                entity.getId() != null ? entity.getId() : 0L,
                entity.getCampaignId(),
                entity.getCompanyId(),
                entity.getName(),
                entity.getAiAgentId(),
                entity.getPromptOverride(),
                entity.getTtsVoiceId(),
                entity.getTrafficWeight(),
                entity.getCallsCount(),
                entity.getAnsweredCount(),
                entity.getConvertedCount(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
