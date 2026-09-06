package uz.murodjon.robotcallv2.campaign.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.campaign.application.mapper.CampaignTargetMapper;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignTargetRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTargetStats;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetFilter;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignTargetEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository.CampaignJpaRepository;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository.CampaignTargetJpaRepository;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository.CampaignTargetSummaryProjection;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.contact.infrastructure.persistence.entity.ContactEntity;
import uz.murodjon.robotcallv2.contact.infrastructure.persistence.repository.ContactJpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CampaignTargetRepositoryAdapter implements CampaignTargetRepository {

    private final CampaignTargetJpaRepository campaignTargetJpaRepository;
    private final CampaignJpaRepository campaignJpaRepository;
    private final ContactJpaRepository contactJpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final CampaignTargetMapper mapper;

    public CampaignTargetRepositoryAdapter(CampaignTargetJpaRepository campaignTargetJpaRepository,
                                         CampaignJpaRepository campaignJpaRepository,
                                         ContactJpaRepository contactJpaRepository,
                                         CompanyJpaRepository companyJpaRepository,
                                         CampaignTargetMapper mapper) {
        this.campaignTargetJpaRepository = campaignTargetJpaRepository;
        this.campaignJpaRepository = campaignJpaRepository;
        this.contactJpaRepository = contactJpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public long add(long companyId, CampaignTarget target) {
        CampaignEntity campaign = campaignJpaRepository.getReferenceById(target.campaignId());
        ContactEntity contact = contactJpaRepository.getReferenceById(target.clientId());
        CompanyEntity comp = companyJpaRepository.getReferenceById(companyId);

        CampaignTargetEntity entity = new CampaignTargetEntity();
        entity.setCampaign(campaign);
        entity.setContact(contact);
        entity.setCompany(comp);
        entity.setPhone(target.phone());
        entity.setLanguage(target.language());
        entity.setContextData(target.contextData() != null ? target.contextData() : "{}");
        entity.setStatus(target.status());
        entity.setAttempts(target.attempts());
        entity.setDoNotCall(target.doNotCall());
        entity.setCreatedAt(Instant.now());
        return campaignTargetJpaRepository.save(entity).getId();
    }

    @Override
    public CampaignTarget find(long companyId, long id) {
        return campaignTargetJpaRepository.findByIdAndCompanyId(id, companyId).map(mapper::toCampaignTarget).orElse(null);
    }

    @Override
    public void resetTargetsForRecurrence(long campaignId) {
        campaignTargetJpaRepository.resetTargetsForRecurrence(campaignId);
    }

    @Override
    public List<CampaignTarget> findByCampaign(long companyId, long campaignId, TargetFilter filter) {
        return campaignTargetJpaRepository.findByCampaignIdAndCompanyId(campaignId, companyId, filter.pageable()).stream()
                .map(mapper::toCampaignTarget)
                .toList();
    }

    @Override
    public long countByCampaign(long companyId, long campaignId) {
        return campaignTargetJpaRepository.countByCampaignIdAndCompanyId(campaignId, companyId);
    }

    @Override
    public long countActive(long campaignId) {
        return campaignTargetJpaRepository.countByCampaignIdAndStatusIn(campaignId, List.of(TargetStatus.PENDING, TargetStatus.IN_PROGRESS));
    }

    @Override
    public List<CampaignTarget> claimDue(long campaignId, int limit) {
        return campaignTargetJpaRepository.claimDue(campaignId, limit).stream().map(mapper::toCampaignTarget).toList();
    }

    @Override
    public int clearSchedule(long campaignId) {
        return campaignTargetJpaRepository.clearSchedule(campaignId);
    }

    @Override
    public void updateStatus(long id, TargetStatus status, Instant nextAttemptAt) {
        campaignTargetJpaRepository.updateStatus(id, status, nextAttemptAt);
    }

    @Override
    public void setDoNotCall(long companyId, long id) {
        campaignTargetJpaRepository.setDoNotCall(id, companyId);
    }

    @Override
    public int deleteByCampaignId(long companyId, long campaignId) {
        return campaignTargetJpaRepository.deleteByCampaignId(campaignId, companyId);
    }

    @Override
    public Map<Long, CampaignTargetStats> statsByCampaignIds(long companyId, Collection<Long> campaignIds) {
        if (campaignIds == null || campaignIds.isEmpty()) {
            return Map.of();
        }
        List<CampaignTargetSummaryProjection> summaries =
                campaignTargetJpaRepository.summarizeByCampaignIdsAndCompanyId(campaignIds, companyId);
        Map<Long, CampaignTargetStats> result = new HashMap<>();
        for (CampaignTargetSummaryProjection s : summaries) {
            if (s.getCampaignId() != null) {
                result.put(s.getCampaignId(), new CampaignTargetStats(
                        s.getTotalTargets(),
                        s.getCalledTargets(),
                        s.getPendingTargets(),
                        s.getCompletedTargets()
                ));
            }
        }
        return result;
    }

    @Override
    public CampaignTargetStats statsByCampaignId(long companyId, long campaignId) {
        return campaignTargetJpaRepository.summarizeByCampaignIdAndCompanyId(campaignId, companyId)
                .map(s -> new CampaignTargetStats(
                        s.getTotalTargets(),
                        s.getCalledTargets(),
                        s.getPendingTargets(),
                        s.getCompletedTargets()
                ))
                .orElse(CampaignTargetStats.ZERO);
    }
}
