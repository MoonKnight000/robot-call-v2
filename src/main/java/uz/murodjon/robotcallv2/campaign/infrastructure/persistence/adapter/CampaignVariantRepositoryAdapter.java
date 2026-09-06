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
        return toDomain(saved);
    }

    @Override
    public Optional<CampaignVariant> findByIdAndCompanyId(long id, long companyId) {
        return jpaRepository.findByIdAndCompanyId(id, companyId).map(this::toDomain);
    }

    @Override
    public List<CampaignVariant> findAllByCampaignIdAndCompanyId(long campaignId, long companyId) {
        return jpaRepository.findAllByCampaignIdAndCompanyId(campaignId, companyId).stream()
                .map(this::toDomain)
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

    private CampaignVariant toDomain(CampaignVariantEntity e) {
        return new CampaignVariant(
                e.getId() != null ? e.getId() : 0L,
                e.getCampaignId(),
                e.getCompanyId(),
                e.getName(),
                e.getAiAgentId(),
                e.getPromptOverride(),
                e.getTtsVoiceId(),
                e.getTrafficWeight(),
                e.getCallsCount(),
                e.getAnsweredCount(),
                e.getConvertedCount(),
                e.isActive(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
