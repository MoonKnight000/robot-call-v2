package uz.murodjon.robotcallv2.conversion.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallAttemptEntity;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallAttemptJpaRepository;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignVariantEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository.CampaignJpaRepository;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository.CampaignVariantJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.conversion.application.mapper.ConversionMapper;
import uz.murodjon.robotcallv2.conversion.application.port.output.ConversionRepository;
import uz.murodjon.robotcallv2.conversion.domain.entity.ConversionAttribution;
import uz.murodjon.robotcallv2.conversion.domain.entity.ConversionEvent;
import uz.murodjon.robotcallv2.conversion.domain.entity.VariantConversions;
import uz.murodjon.robotcallv2.conversion.infrastructure.persistence.entity.ConversionAttributionEntity;
import uz.murodjon.robotcallv2.conversion.infrastructure.persistence.entity.ConversionEventEntity;
import uz.murodjon.robotcallv2.conversion.infrastructure.persistence.repository.ConversionAttributionJpaRepository;
import uz.murodjon.robotcallv2.conversion.infrastructure.persistence.repository.ConversionEventJpaRepository;

import java.util.List;
import java.util.Optional;

@Component
public class ConversionRepositoryAdapter implements ConversionRepository {

    private final ConversionEventJpaRepository eventJpaRepository;
    private final ConversionAttributionJpaRepository attributionJpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final CampaignJpaRepository campaignJpaRepository;
    private final CampaignVariantJpaRepository campaignVariantJpaRepository;
    private final CallAttemptJpaRepository callAttemptJpaRepository;
    private final ConversionMapper mapper;

    public ConversionRepositoryAdapter(ConversionEventJpaRepository eventJpaRepository,
                                       ConversionAttributionJpaRepository attributionJpaRepository,
                                       CompanyJpaRepository companyJpaRepository,
                                       CampaignJpaRepository campaignJpaRepository,
                                       CampaignVariantJpaRepository campaignVariantJpaRepository,
                                       CallAttemptJpaRepository callAttemptJpaRepository,
                                       ConversionMapper mapper) {
        this.eventJpaRepository = eventJpaRepository;
        this.attributionJpaRepository = attributionJpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.campaignJpaRepository = campaignJpaRepository;
        this.campaignVariantJpaRepository = campaignVariantJpaRepository;
        this.callAttemptJpaRepository = callAttemptJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public ConversionEvent saveEvent(ConversionEvent event) {
        CompanyEntity company = companyJpaRepository.getReferenceById(event.companyId());
        return mapper.toConversionEvent(eventJpaRepository.save(mapper.toEntity(event, company)));
    }

    @Override
    public Optional<ConversionEvent> findEventByDedupeKey(long companyId, String dedupeKey) {
        return eventJpaRepository.findByCompanyIdAndDedupeKey(companyId, dedupeKey)
                .map(mapper::toConversionEvent);
    }

    @Override
    public ConversionAttribution saveAttribution(ConversionAttribution attribution) {
        CompanyEntity company = companyJpaRepository.getReferenceById(attribution.companyId());
        ConversionAttributionEntity entity = mapper.toEntity(attribution, company,
                eventJpaRepository.getReferenceById(attribution.conversionEventId()),
                campaignReference(attribution.campaignId()), variantReference(attribution.variantId()),
                callAttemptReference(attribution.callAttemptId()));
        return mapper.toConversionAttribution(attributionJpaRepository.save(entity));
    }

    @Override
    public Optional<ConversionAttribution> findAttributionByEventId(long conversionEventId) {
        return attributionJpaRepository.findByConversionEventId(conversionEventId)
                .map(mapper::toConversionAttribution);
    }

    @Override
    public List<VariantConversions> findConversionsByVariant(long companyId, long campaignId) {
        return attributionJpaRepository.findConversionsByVariant(companyId, campaignId).stream()
                .map(row -> new VariantConversions(
                        row[0] != null ? ((Number) row[0]).longValue() : null,
                        ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue()))
                .toList();
    }

    /** Null when the conversion could not be attributed to a campaign. */
    private CampaignEntity campaignReference(Long id) {
        return id != null ? campaignJpaRepository.getReferenceById(id) : null;
    }

    /** Null when the attributed campaign ran no A/B variant. */
    private CampaignVariantEntity variantReference(Long id) {
        return id != null ? campaignVariantJpaRepository.getReferenceById(id) : null;
    }

    /** Null when no call could be tied to the conversion. */
    private CallAttemptEntity callAttemptReference(Long id) {
        return id != null ? callAttemptJpaRepository.getReferenceById(id) : null;
    }
}
