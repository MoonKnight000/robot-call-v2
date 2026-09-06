package uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.report.application.dto.CreateReportScheduleRequest;
import uz.murodjon.robotcallv2.report.domain.entity.ReportScheduleFilter;
import uz.murodjon.robotcallv2.report.application.mapper.ReportScheduleMapper;
import uz.murodjon.robotcallv2.report.application.port.output.ReportScheduleRepository;
import uz.murodjon.robotcallv2.report.domain.entity.ReportSchedule;
import uz.murodjon.robotcallv2.report.infrastructure.persistence.entity.ReportScheduleEntity;
import uz.murodjon.robotcallv2.report.infrastructure.persistence.repository.ReportScheduleJpaRepository;

import java.time.Instant;
import java.util.List;

@Component
public class ReportScheduleRepositoryAdapter implements ReportScheduleRepository {

    private final ReportScheduleJpaRepository jpaRepository;
    private final ReportScheduleMapper mapper;
    private final EntityManager em;

    public ReportScheduleRepositoryAdapter(ReportScheduleJpaRepository jpaRepository,
                                           ReportScheduleMapper mapper, EntityManager em) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.em = em;
    }

    @Override
    public long create(long companyId, CreateReportScheduleRequest r) {
        ReportScheduleEntity entity = new ReportScheduleEntity();
        entity.setCompany(em.getReference(CompanyEntity.class, companyId));
        entity.setEmail(r.email());
        entity.setPeriodicity(r.periodicity());
        entity.setFormat(r.format() != null && !r.format().isBlank() ? r.format().toLowerCase() : "pdf");
        if (r.campaignId() != null) {
            entity.setCampaign(em.getReference(CampaignEntity.class, r.campaignId()));
        }
        entity.setEnabled(true);
        entity.setCreatedAt(Instant.now());
        return jpaRepository.save(entity).getId();
    }

    @Override
    public ReportSchedule find(long companyId, long id) {
        return jpaRepository.findByIdAndCompany_Id(id, companyId).map(mapper::toReportSchedule).orElse(null);
    }

    @Override
    public List<ReportSchedule> findAll(long companyId, ReportScheduleFilter filter) {
        return jpaRepository.findByCompany_Id(companyId, filter.pageable()).stream()
                .map(mapper::toReportSchedule)
                .toList();
    }

    @Override
    public long count(long companyId, ReportScheduleFilter filter) {
        return jpaRepository.countByCompany_Id(companyId);
    }

    @Override
    public void disable(long companyId, long id) {
        jpaRepository.findByIdAndCompany_Id(id, companyId).ifPresent(entity -> {
            entity.setEnabled(false);
            jpaRepository.save(entity);
        });
    }

    @Override
    public List<ReportSchedule> findEnabled() {
        return jpaRepository.findByEnabledTrue().stream().map(mapper::toReportSchedule).toList();
    }

    @Override
    public void markSent(long id, Instant sentAt) {
        jpaRepository.findById(id).ifPresent(entity -> {
            entity.setLastSentAt(sentAt);
            jpaRepository.save(entity);
        });
    }
}
