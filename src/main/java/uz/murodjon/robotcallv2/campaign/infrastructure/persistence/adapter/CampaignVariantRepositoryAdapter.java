package uz.murodjon.robotcallv2.campaign.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentEntity;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository.AiAgentJpaRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignVariantRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignVariant;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignVariantEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository.CampaignJpaRepository;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository.CampaignVariantJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.voice.infrastructure.persistence.entity.TtsVoiceEntity;
import uz.murodjon.robotcallv2.voice.infrastructure.persistence.repository.TtsVoiceJpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class CampaignVariantRepositoryAdapter implements CampaignVariantRepository {

    private final CampaignVariantJpaRepository jpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final CampaignJpaRepository campaignJpaRepository;
    private final AiAgentJpaRepository aiAgentJpaRepository;
    private final TtsVoiceJpaRepository ttsVoiceJpaRepository;

    public CampaignVariantRepositoryAdapter(CampaignVariantJpaRepository jpaRepository,
                                            CompanyJpaRepository companyJpaRepository,
                                            CampaignJpaRepository campaignJpaRepository,
                                            AiAgentJpaRepository aiAgentJpaRepository,
                                            TtsVoiceJpaRepository ttsVoiceJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.campaignJpaRepository = campaignJpaRepository;
        this.aiAgentJpaRepository = aiAgentJpaRepository;
        this.ttsVoiceJpaRepository = ttsVoiceJpaRepository;
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
        entity.setCampaign(campaignJpaRepository.getReferenceById(variant.campaignId()));
        entity.setCompany(companyJpaRepository.getReferenceById(variant.companyId()));
        entity.setName(variant.name());
        entity.setAiAgent(aiAgentReference(variant.aiAgentId()));
        entity.setPromptOverride(variant.promptOverride());
        entity.setTtsVoice(ttsVoiceReference(variant.ttsVoiceId()));
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

    /** Null when the variant keeps the campaign's own agent. */
    private AiAgentEntity aiAgentReference(Long id) {
        return id != null ? aiAgentJpaRepository.getReferenceById(id) : null;
    }

    /** Null when the variant keeps the agent's own voice. */
    private TtsVoiceEntity ttsVoiceReference(String id) {
        return id != null ? ttsVoiceJpaRepository.getReferenceById(id) : null;
    }
}
