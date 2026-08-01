package uz.murodjon.uysotvoice.campaign.repository;

import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.campaign.dto.TargetFilter;
import uz.murodjon.uysotvoice.campaign.dto.TargetRow;
import uz.murodjon.uysotvoice.campaign.entity.CampaignTarget;
import uz.murodjon.uysotvoice.campaign.enums.TargetStatus;
import uz.murodjon.uysotvoice.company.service.CurrentCompany;

import java.time.Instant;
import java.util.List;

/** JPA-backed DAO for {@code campaign_target} (PROJECT.md §6). */
@Repository
public class CampaignTargetRepository {

    private final CampaignTargetJpaRepository jpa;
    private final CampaignJpaRepository campaigns;
    private final CurrentCompany company;

    public CampaignTargetRepository(CampaignTargetJpaRepository jpa, CampaignJpaRepository campaigns,
                                    CurrentCompany company) {
        this.jpa = jpa;
        this.campaigns = campaigns;
        this.company = company;
    }

    public long add(long campaignId, long clientId, String phone, String language, String contextDataJson) {
        CampaignTarget entity = new CampaignTarget();
        entity.setCampaign(campaigns.getReferenceById(campaignId));
        entity.setClientId(clientId);
        entity.setPhone(phone);
        entity.setLanguage(language);
        entity.setContextData(contextDataJson != null ? contextDataJson : "{}");
        entity.setStatus(TargetStatus.PENDING);
        entity.setAttempts(0);
        entity.setDoNotCall(false);
        entity.setCreatedAt(Instant.now());
        entity.setCompanyId(company.id());
        return jpa.save(entity).getId();
    }

    /**
     * Scoped to the current company — this is the one lookup path reachable by a bare
     * target id with no campaign in the URL ({@code POST /api/targets/{id}/do-not-call}),
     * so without this check another company's target id would be a valid opt-out target.
     */
    public TargetRow find(long id) {
        return jpa.findByIdAndCompanyId(id, company.id()).map(CampaignTargetRepository::toRow).orElse(null);
    }

    public List<TargetRow> findByCampaign(long campaignId, TargetFilter filter) {
        return jpa.findByCampaign_IdAndCompanyId(campaignId, company.id(), filter.pageable()).stream()
                .map(CampaignTargetRepository::toRow)
                .toList();
    }

    public long countByCampaign(long campaignId) {
        return jpa.countByCampaign_IdAndCompanyId(campaignId, company.id());
    }

    /**
     * Atomically claim up to {@code limit} targets that are ready to dial: PENDING,
     * not opted out (the per-target flag <em>and</em> the phone-level list from
     * §11.4), and past their retry time. Claimed rows come back already marked
     * IN_PROGRESS with the attempt counted.
     *
     * <p>Deliberately not scoped by {@link CurrentCompany} — the dialer ticks every
     * company's active campaigns in turn, not just the one a request happens to be
     * scoped to; {@code campaignId} alone already pins this to one company's data.
     * See {@link CampaignTargetJpaRepository#claimDue} for how the atomicity and
     * opt-out check are enforced.
     */
    public List<TargetRow> claimDue(long campaignId, int limit) {
        return jpa.claimDue(campaignId, limit).stream().map(CampaignTargetRepository::toRow).toList();
    }

    /** Internal (dialer outcome application) — not scoped, see {@link #claimDue}. */
    public void updateStatus(long id, TargetStatus status, Instant nextAttemptAt) {
        jpa.updateStatus(id, status, nextAttemptAt);
    }

    /** Scoped to the current company — reached by a bare target id, see {@link #find}. */
    public void setDoNotCall(long id) {
        jpa.setDoNotCall(id, company.id());
    }

    private static TargetRow toRow(CampaignTarget e) {
        return new TargetRow(
                e.getId(),
                e.getCampaign().getId(),
                e.getClientId(),
                e.getPhone(),
                e.getLanguage(),
                e.getContextData(),
                e.getStatus(),
                e.getAttempts(),
                e.isDoNotCall());
    }
}
