package uz.murodjon.robotcallv2.partner.infrastructure.persistence.adapter;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.partner.application.port.output.PartnerRepository;
import uz.murodjon.robotcallv2.partner.domain.entity.PartnerBranding;
import uz.murodjon.robotcallv2.partner.domain.entity.PartnerClient;
import uz.murodjon.robotcallv2.partner.domain.entity.PartnerClientFilter;
import uz.murodjon.robotcallv2.partner.infrastructure.persistence.entity.PartnerBrandingEntity;
import uz.murodjon.robotcallv2.partner.infrastructure.persistence.entity.PartnerClientEntity;
import uz.murodjon.robotcallv2.partner.infrastructure.persistence.mapper.PartnerBrandingEntityMapper;
import uz.murodjon.robotcallv2.partner.infrastructure.persistence.mapper.PartnerClientEntityMapper;
import uz.murodjon.robotcallv2.partner.infrastructure.persistence.repository.PartnerBrandingJpaRepository;
import uz.murodjon.robotcallv2.partner.infrastructure.persistence.repository.PartnerClientJpaRepository;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.time.Clock;
import java.time.Instant;

@Component
public class PartnerRepositoryAdapter implements PartnerRepository {

    private final PartnerBrandingJpaRepository partnerBrandingJpaRepository;
    private final PartnerClientJpaRepository partnerClientJpaRepository;
    private final PartnerBrandingEntityMapper partnerBrandingEntityMapper;
    private final PartnerClientEntityMapper partnerClientEntityMapper;
    private final Clock clock;

    public PartnerRepositoryAdapter(PartnerBrandingJpaRepository partnerBrandingJpaRepository,
                                    PartnerClientJpaRepository partnerClientJpaRepository,
                                    PartnerBrandingEntityMapper partnerBrandingEntityMapper,
                                    PartnerClientEntityMapper partnerClientEntityMapper, Clock clock) {
        this.partnerBrandingJpaRepository = partnerBrandingJpaRepository;
        this.partnerClientJpaRepository = partnerClientJpaRepository;
        this.partnerBrandingEntityMapper = partnerBrandingEntityMapper;
        this.partnerClientEntityMapper = partnerClientEntityMapper;
        this.clock = clock;
    }

    @Override
    public PartnerBranding findBrandingByCompanyId(long companyId) {
        return partnerBrandingJpaRepository.findById(companyId)
                .map(partnerBrandingEntityMapper::toPartnerBranding)
                .orElse(null);
    }

    @Override
    public PartnerBranding findBrandingByDomain(String domain) {
        return partnerBrandingJpaRepository.findByDomain(domain)
                .map(partnerBrandingEntityMapper::toPartnerBranding)
                .orElse(null);
    }

    @Override
    public boolean existsBrandingByDomainAndCompanyIdNot(String domain, long companyId) {
        return partnerBrandingJpaRepository.existsByDomainAndCompanyIdNot(domain, companyId);
    }

    @Override
    @Transactional
    public PartnerBranding upsertBranding(long companyId, PartnerBranding branding) {
        PartnerBrandingEntity entity = partnerBrandingJpaRepository.findById(companyId).orElseGet(() -> {
            PartnerBrandingEntity created = new PartnerBrandingEntity();
            created.setCompanyId(companyId);
            return created;
        });
        partnerBrandingEntityMapper.copyInto(branding, entity);
        entity.setUpdatedAt(Instant.now(clock));
        return partnerBrandingEntityMapper.toPartnerBranding(partnerBrandingJpaRepository.save(entity));
    }

    @Override
    public void createClient(long partnerCompanyId, long clientCompanyId) {
        PartnerClientEntity entity = new PartnerClientEntity();
        entity.setClientCompanyId(clientCompanyId);
        entity.setPartnerCompanyId(partnerCompanyId);
        entity.setCreatedAt(Instant.now(clock));
        partnerClientJpaRepository.save(entity);
    }

    @Override
    public PartnerClient findClientByClientCompanyId(long clientCompanyId) {
        return partnerClientJpaRepository.findById(clientCompanyId)
                .map(partnerClientEntityMapper::toPartnerClient)
                .orElse(null);
    }

    @Override
    public PageableData<PartnerClient> findClientPage(long partnerCompanyId, PartnerClientFilter filter) {
        Page<PartnerClientEntity> page = partnerClientJpaRepository.findAllByPartnerCompanyId(partnerCompanyId,
                filter.pageable());
        return PageableData.of(page.getContent().stream().map(partnerClientEntityMapper::toPartnerClient).toList(),
                filter.pageOrDefault(), filter.sizeOrDefault(), page.getTotalElements());
    }
}
