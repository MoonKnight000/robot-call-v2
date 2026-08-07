package uz.murodjon.uysotvoice.report.repository;

import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.report.dto.CreateReportScheduleRequest;
import uz.murodjon.uysotvoice.report.dto.ReportSchedule;
import uz.murodjon.uysotvoice.report.dto.ReportScheduleFilter;
import uz.murodjon.uysotvoice.report.entity.ReportScheduleEntity;

import java.time.Instant;
import java.util.List;

/** JPA-backed DAO for {@code report_schedule} (§10.10 "Jadval bo'yicha yuborish"). */
@Repository
public class ReportScheduleRepository {

    private final ReportScheduleJpaRepository jpa;
    private final CurrentCompany company;

    public ReportScheduleRepository(ReportScheduleJpaRepository jpa, CurrentCompany company) {
        this.jpa = jpa;
        this.company = company;
    }

    public long create(CreateReportScheduleRequest r) {
        ReportScheduleEntity entity = new ReportScheduleEntity();
        entity.setCompanyId(company.id());
        entity.setEmail(r.email());
        entity.setPeriodicity(r.periodicity());
        entity.setFormat(r.format() != null && !r.format().isBlank() ? r.format().toLowerCase() : "pdf");
        entity.setCampaignId(r.campaignId());
        entity.setEnabled(true);
        entity.setCreatedAt(Instant.now());
        return jpa.save(entity).getId();
    }

    /** Scoped to the current company — another company's id reads as missing, not found. */
    public ReportSchedule find(long id) {
        return jpa.findByIdAndCompanyId(id, company.id()).map(ReportScheduleRepository::toRow).orElse(null);
    }

    public List<ReportSchedule> findAll(ReportScheduleFilter filter) {
        return jpa.findByCompanyId(company.id(), filter.pageable()).stream()
                .map(ReportScheduleRepository::toRow)
                .toList();
    }

    public long count(ReportScheduleFilter filter) {
        return jpa.countByCompanyId(company.id());
    }

    /** No-op if {@code id} does not belong to the current company. */
    public void disable(long id) {
        jpa.findByIdAndCompanyId(id, company.id()).ifPresent(entity -> {
            entity.setEnabled(false);
            jpa.save(entity);
        });
    }

    /**
     * Every enabled schedule, across every company (mirrors {@code
     * CampaignRepository#findActive} — the dispatch sweep must service every company's
     * schedules, not just the one the current request happens to be scoped to).
     */
    public List<ReportSchedule> findEnabled() {
        return jpa.findByEnabledTrue().stream().map(ReportScheduleRepository::toRow).toList();
    }

    /** Record that {@code id}'s email actually went out just now. */
    public void markSent(long id, Instant sentAt) {
        jpa.findById(id).ifPresent(entity -> {
            entity.setLastSentAt(sentAt);
            jpa.save(entity);
        });
    }

    private static ReportSchedule toRow(ReportScheduleEntity e) {
        return new ReportSchedule(e.getId(), e.getCompanyId(), e.getEmail(), e.getPeriodicity(), e.getFormat(),
                e.getCampaignId(), e.isEnabled(), e.getLastSentAt(), e.getCreatedAt());
    }
}
