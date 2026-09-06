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
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
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

    private final CampaignTargetJpaRepository jpa;
    private final CampaignJpaRepository campaigns;
    private final ContactJpaRepository contacts;
    private final CompanyJpaRepository companies;
    private final CurrentCompany company;
    private final CampaignTargetMapper mapper;

    public CampaignTargetRepositoryAdapter(CampaignTargetJpaRepository jpa,
                                         CampaignJpaRepository campaigns,
                                         ContactJpaRepository contacts,
                                         CompanyJpaRepository companies,
                                         CurrentCompany company,
                                         CampaignTargetMapper mapper) {
        this.jpa = jpa;
        this.campaigns = campaigns;
        this.contacts = contacts;
        this.companies = companies;
        this.company = company;
        this.mapper = mapper;
    }

    @Override
    public long add(long campaignId, long clientId, String phone, String language, String contextDataJson) {
        CampaignEntity campaign = campaigns.getReferenceById(campaignId);
        ContactEntity contact = contacts.getReferenceById(clientId);
        CompanyEntity comp = companies.getReferenceById(company.id());

        CampaignTargetEntity entity = new CampaignTargetEntity();
        entity.setCampaign(campaign);
        entity.setContact(contact);
        entity.setCompany(comp);
        entity.setPhone(phone);
        entity.setLanguage(language);
        entity.setContextData(contextDataJson != null ? contextDataJson : "{}");
        entity.setStatus(TargetStatus.PENDING);
        entity.setAttempts(0);
        entity.setDoNotCall(false);
        entity.setCreatedAt(Instant.now());
        return jpa.save(entity).getId();
    }

    @Override
    public CampaignTarget find(long id) {
        return jpa.findByIdAndCompanyId(id, company.id()).map(mapper::toDomain).orElse(null);
    }

    @Override
    public void resetTargetsForRecurrence(long campaignId) {
        jpa.resetTargetsForRecurrence(campaignId);
    }

    @Override
    public List<CampaignTarget> findByCampaign(long campaignId, TargetFilter filter) {
        return jpa.findByCampaignIdAndCompanyId(campaignId, company.id(), filter.pageable()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public long countByCampaign(long campaignId) {
        return jpa.countByCampaignIdAndCompanyId(campaignId, company.id());
    }

    @Override
    public long countActive(long campaignId) {
        return jpa.countByCampaignIdAndStatusIn(campaignId, List.of(TargetStatus.PENDING, TargetStatus.IN_PROGRESS));
    }

    @Override
    public List<CampaignTarget> claimDue(long campaignId, int limit) {
        return jpa.claimDue(campaignId, limit).stream().map(mapper::toDomain).toList();
    }

    @Override
    public int clearSchedule(long campaignId) {
        return jpa.clearSchedule(campaignId);
    }

    @Override
    public void updateStatus(long id, TargetStatus status, Instant nextAttemptAt) {
        jpa.updateStatus(id, status, nextAttemptAt);
    }

    @Override
    public void setDoNotCall(long id) {
        jpa.setDoNotCall(id, company.id());
    }

    @Override
    public int deleteByCampaignId(long campaignId) {
        return jpa.deleteByCampaignId(campaignId, company.id());
    }

    @Override
    public Map<Long, CampaignTargetStats> statsByCampaignIds(Collection<Long> campaignIds) {
        if (campaignIds == null || campaignIds.isEmpty()) {
            return Map.of();
        }
        List<CampaignTargetSummaryProjection> summaries =
                jpa.summarizeByCampaignIdsAndCompanyId(campaignIds, company.id());
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
    public CampaignTargetStats statsByCampaignId(long campaignId) {
        return jpa.summarizeByCampaignIdAndCompanyId(campaignId, company.id())
                .map(s -> new CampaignTargetStats(
                        s.getTotalTargets(),
                        s.getCalledTargets(),
                        s.getPendingTargets(),
                        s.getCompletedTargets()
                ))
                .orElse(CampaignTargetStats.ZERO);
    }
}
