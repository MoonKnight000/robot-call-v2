package uz.murodjon.uysotvoice.campaign.repository;

import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.campaign.dto.CampaignFilter;
import uz.murodjon.uysotvoice.campaign.dto.CampaignRow;
import uz.murodjon.uysotvoice.campaign.entity.Campaign;
import uz.murodjon.uysotvoice.company.service.CurrentCompany;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

/** JPA-backed DAO for {@code campaign} (PROJECT.md §6). */
@Repository
public class CampaignRepository {

    private final CampaignJpaRepository jpa;
    private final CurrentCompany company;

    public CampaignRepository(CampaignJpaRepository jpa, CurrentCompany company) {
        this.jpa = jpa;
        this.company = company;
    }

    public long create(String name, String type, String goalPrompt, String scriptConfigJson,
                       String defaultLanguage, LocalTime windowStart, LocalTime windowEnd,
                       String dialDays, int maxAttempts, int retryIntervalHours, int maxConcurrentCalls,
                       String ttsVoice, int dailyCallCap) {
        Campaign entity = new Campaign();
        entity.setName(name);
        entity.setType(type);
        entity.setStatus("DRAFT");
        entity.setGoalPrompt(goalPrompt);
        entity.setScriptConfig(scriptConfigJson != null ? scriptConfigJson : "{}");
        entity.setDefaultLanguage(defaultLanguage);
        entity.setDialWindowStart(windowStart);
        entity.setDialWindowEnd(windowEnd);
        entity.setDialDays(dialDays);
        entity.setMaxAttempts(maxAttempts);
        entity.setRetryIntervalHours(retryIntervalHours);
        entity.setMaxConcurrentCalls(maxConcurrentCalls);
        entity.setTtsVoice(ttsVoice);
        entity.setDailyCallCap(dailyCallCap);
        entity.setCreatedAt(Instant.now());
        entity.setCompanyId(company.id());
        return jpa.save(entity).getId();
    }

    /** Scoped to the current company (§ROADMAP B.2) — another company's id is a 404, not a 200. */
    public CampaignRow find(long id) {
        return jpa.findByIdAndCompanyId(id, company.id()).map(CampaignRepository::toRow).orElse(null);
    }

    public List<CampaignRow> findAll(CampaignFilter filter) {
        return jpa.findByCompanyId(company.id(), filter.pageable()).stream()
                .map(CampaignRepository::toRow)
                .toList();
    }

    public long count() {
        return jpa.countByCompanyId(company.id());
    }

    /**
     * Every active campaign, across every company — the dialer's own dispatch tick reads
     * this, and it must service every company's active work, not just the one the current
     * request happens to be scoped to. Deliberately unfiltered by {@link CurrentCompany}.
     */
    public List<CampaignRow> findActive() {
        return jpa.findByStatusOrderById("ACTIVE").stream().map(CampaignRepository::toRow).toList();
    }

    public void updateStatus(long id, String status) {
        jpa.updateStatus(id, status, company.id());
    }

    private static CampaignRow toRow(Campaign e) {
        return new CampaignRow(
                e.getId(),
                e.getName(),
                e.getType(),
                e.getStatus(),
                e.getGoalPrompt(),
                e.getDefaultLanguage(),
                e.getDialWindowStart(),
                e.getDialWindowEnd(),
                e.getDialDays(),
                e.getMaxAttempts(),
                e.getRetryIntervalHours(),
                e.getMaxConcurrentCalls(),
                e.getTtsVoice(),
                e.getDailyCallCap());
    }
}
