package uz.murodjon.uysotvoice.campaign.repository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.campaign.dto.CampaignFilter;
import uz.murodjon.uysotvoice.campaign.dto.Campaign;
import uz.murodjon.uysotvoice.campaign.entity.CampaignEntity;
import uz.murodjon.uysotvoice.campaign.enums.CampaignStatus;
import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.user.service.CurrentUser;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** JPA-backed DAO for {@code campaign} (PROJECT.md §6). */
@Repository
public class CampaignRepository {

    private final CampaignJpaRepository jpa;
    private final CurrentCompany company;
    private final CurrentUser currentUser;

    public CampaignRepository(CampaignJpaRepository jpa, CurrentCompany company, CurrentUser currentUser) {
        this.jpa = jpa;
        this.company = company;
        this.currentUser = currentUser;
    }
    /**
     * {@code row.id()}, {@code row.status()}, {@code row.companyId()} and {@code row.createdBy()}
     * are ignored — a new campaign is always DRAFT, owned by the current company, created by
     * whoever the current request is authenticated as ({@code null} for the machine-to-machine
     * {@code X-Api-Key}, which has no associated person).
     */
    public long create(Campaign row) {
        CampaignEntity entity = new CampaignEntity();
        applyEditableFields(entity, row);
        entity.setType(row.type());
        entity.setStatus(CampaignStatus.DRAFT);
        entity.setScriptConfig("{}");
        entity.setCreatedAt(Instant.now());
        entity.setCompanyId(company.id());
        entity.setScenarioId(row.scenarioId());
        entity.setCreatedBy(currentUser.id().orElse(null));
        return jpa.save(entity).getId();
    }

    /** Scoped to the current company (§ROADMAP B.2) — another company's id is a 404, not a 200. */
    public Campaign find(long id) {
        return jpa.findByIdAndCompanyId(id, company.id()).map(CampaignRepository::toRow).orElse(null);
    }

    public List<Campaign> findAll(CampaignFilter filter) {
        return jpa.findByCompanyId(company.id(), filter.status(), filter.pageable()).stream()
                .map(CampaignRepository::toRow)
                .toList();
    }

    public long count() {
        return jpa.countByCompanyId(company.id(), null);
    }

    public long count(CampaignFilter filter) {
        return jpa.countByCompanyId(company.id(), filter.status());
    }

    /** Command palette (UI-DESIGN §11.9) — top {@code limit} campaigns whose name matches {@code q}. */
    public List<Campaign> searchByName(String q, int limit) {
        String pattern = "%" + q.trim().toLowerCase() + "%";
        return jpa.searchByCompanyId(company.id(), pattern, PageRequest.of(0, limit)).stream()
                .map(CampaignRepository::toRow)
                .toList();
    }

    /**
     * Every active campaign, across every company — the dialer's own dispatch tick reads
     * this, and it must service every company's active work, not just the one the current
     * request happens to be scoped to. Deliberately unfiltered by {@link CurrentCompany}.
     */
    public List<Campaign> findActive() {
        return jpa.findByStatusOrderById(CampaignStatus.ACTIVE).stream().map(CampaignRepository::toRow).toList();
    }

    public void updateStatus(long id, CampaignStatus status) {
        jpa.updateStatus(id, status, company.id());
    }

    /**
     * No-op if {@code id} does not belong to the current company. {@code row.type()},
     * {@code row.status()}, {@code row.scenarioId()} and {@code row.companyId()} are
     * ignored — none of those are editable after creation.
     */
    public void update(long id, Campaign row) {
        jpa.findByIdAndCompanyId(id, company.id()).ifPresent(entity -> {
            applyEditableFields(entity, row);
            jpa.save(entity);
        });
    }

    /** Fields a company can change both at creation and via {@link #update}. */
    private static void applyEditableFields(CampaignEntity entity, Campaign row) {
        entity.setName(row.name());
        entity.setGoalPrompt(row.goalPrompt());
        entity.setDefaultLanguage(row.defaultLanguage());
        entity.setDialWindowStart(row.dialWindowStart());
        entity.setDialWindowEnd(row.dialWindowEnd());
        entity.setDialDays(row.dialDays());
        entity.setMaxAttempts(row.maxAttempts());
        entity.setRetryIntervalHours(row.retryIntervalHours());
        entity.setMaxConcurrentCalls(row.maxConcurrentCalls());
        entity.setTtsVoice(row.ttsVoice());
        entity.setDailyCallCap(row.dailyCallCap());
        entity.setDisclosureEnabled(row.disclosureEnabled());
    }

    private static Campaign toRow(CampaignEntity e) {
        return new Campaign(
                e.getId(),
                e.getName(),
                e.getType(),
                e.getStatus(),
                e.getGoalPrompt(),
                e.getDefaultLanguage(),
                e.getDialWindowStart(),
                e.getDialWindowEnd(),
                Set.copyOf(e.getDialDays()),
                e.getMaxAttempts(),
                e.getRetryIntervalHours(),
                e.getMaxConcurrentCalls(),
                e.getTtsVoice(),
                e.getDailyCallCap(),
                e.getScenarioId(),
                e.getCompanyId(),
                e.isDisclosureEnabled(),
                e.getCreatedBy());
    }
}
