package uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignEntity;
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.report.application.dto.CreateReportScheduleRequest;
import uz.murodjon.robotcallv2.report.application.dto.ReportScheduleFilter;
import uz.murodjon.robotcallv2.report.application.mapper.ReportScheduleMapper;
import uz.murodjon.robotcallv2.report.application.port.output.ReportScheduleRepository;
import uz.murodjon.robotcallv2.report.domain.entity.ReportSchedule;
import uz.murodjon.robotcallv2.report.infrastructure.persistence.entity.ReportScheduleEntity;
import uz.murodjon.robotcallv2.report.infrastructure.persistence.repository.ReportScheduleJpaRepository;

import java.time.Instant;
import java.util.List;

@Component
public class ReportScheduleRepositoryAdapter implements ReportScheduleRepository {

    private final ReportScheduleJpaRepository jpa;
    private final CurrentCompany company;
    private final ReportScheduleMapper mapper;
    private final EntityManager em;

    public ReportScheduleRepositoryAdapter(ReportScheduleJpaRepository jpa, CurrentCompany company,
                                           ReportScheduleMapper mapper, EntityManager em) {
        this.jpa = jpa;
        this.company = company;
        this.mapper = mapper;
        this.em = em;
    }

    @Override
    public long create(CreateReportScheduleRequest r) {
        ReportScheduleEntity entity = new ReportScheduleEntity();
        entity.setCompany(em.getReference(CompanyEntity.class, company.id()));
        entity.setEmail(r.email());
        entity.setPeriodicity(r.periodicity());
        entity.setFormat(r.format() != null && !r.format().isBlank() ? r.format().toLowerCase() : "pdf");
        if (r.campaignId() != null) {
            entity.setCampaign(em.getReference(CampaignEntity.class, r.campaignId()));
        }
        entity.setEnabled(true);
        entity.setCreatedAt(Instant.now());
        return jpa.save(entity).getId();
    }

    @Override
    public ReportSchedule find(long id) {
        return jpa.findByIdAndCompany_Id(id, company.id()).map(mapper::toDomain).orElse(null);
    }

    @Override
    public List<ReportSchedule> findAll(ReportScheduleFilter filter) {
        return jpa.findByCompany_Id(company.id(), filter.pageable()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public long count(ReportScheduleFilter filter) {
        return jpa.countByCompany_Id(company.id());
    }

    @Override
    public void disable(long id) {
        jpa.findByIdAndCompany_Id(id, company.id()).ifPresent(entity -> {
            entity.setEnabled(false);
            jpa.save(entity);
        });
    }

    @Override
    public List<ReportSchedule> findEnabled() {
        return jpa.findByEnabledTrue().stream().map(mapper::toDomain).toList();
    }

    @Override
    public void markSent(long id, Instant sentAt) {
        jpa.findById(id).ifPresent(entity -> {
            entity.setLastSentAt(sentAt);
            jpa.save(entity);
        });
    }
}
