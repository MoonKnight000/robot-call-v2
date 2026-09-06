package uz.murodjon.robotcallv2.campaign.infrastructure.persistence.adapter;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.campaign.application.mapper.CampaignMapper;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignFilter;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository.CampaignJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.entity.UserEntity;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.repository.UserJpaRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class CampaignRepositoryAdapter implements CampaignRepository {

    private final CampaignJpaRepository campaignJpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final CampaignMapper mapper;

    public CampaignRepositoryAdapter(CampaignJpaRepository campaignJpaRepository,
                                     CompanyJpaRepository companyJpaRepository,
                                     UserJpaRepository userJpaRepository,
                                     CampaignMapper mapper) {
        this.campaignJpaRepository = campaignJpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.userJpaRepository = userJpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public long create(long companyId, Campaign row) {
        CompanyEntity company = companyJpaRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMPANY_NOT_FOUND, companyId));
        UserEntity createdBy = row.createdBy() != null
                ? userJpaRepository.findById(row.createdBy()).orElse(null)
                : null;

        CampaignEntity entity = mapper.toEntity(row, company, createdBy);
        entity.setCreatedAt(Instant.now());
        return campaignJpaRepository.save(entity).getId();
    }

    @Override
    public Campaign find(long companyId, long id) {
        return campaignJpaRepository.findByIdAndCompanyId(id, companyId)
                .map(mapper::toCampaign)
                .orElse(null);
    }

    @Override
    public List<Campaign> findAll(long companyId, CampaignFilter filter) {
        Specification<CampaignEntity> spec = buildSpecification(filter, companyId);
        return campaignJpaRepository.findAll(spec, filter.pageable()).stream()
                .map(mapper::toCampaign)
                .toList();
    }

    @Override
    public long count(long companyId) {
        return campaignJpaRepository.countByCompanyId(companyId);
    }

    @Override
    public long count(long companyId, CampaignFilter filter) {
        return campaignJpaRepository.count(buildSpecification(filter, companyId));
    }

    @Override
    public List<Campaign> searchByName(long companyId, String q, int limit) {
        return campaignJpaRepository.searchByCompanyId(companyId, "%" + q.toLowerCase() + "%",
                        PageRequest.of(0, limit))
                .stream()
                .map(mapper::toCampaign)
                .toList();
    }

    @Override
    public List<Campaign> findActive() {
        return campaignJpaRepository.findByStatusOrderById(CampaignStatus.ACTIVE).stream()
                .map(mapper::toCampaign)
                .toList();
    }

    @Override
    public List<Campaign> findRecurring() {
        return campaignJpaRepository.findByRecurrenceTypeNotAndStatusNot(RecurrenceType.ONCE, CampaignStatus.ARCHIVED).stream()
                .map(mapper::toCampaign)
                .toList();
    }

    @Override
    @Transactional
    public void updateStatus(long companyId, long id, CampaignStatus status) {
        campaignJpaRepository.updateStatus(id, status, companyId);
    }

    @Override
    @Transactional
    public void recordRecurrenceRun(long id, Instant lastRunAt, CampaignStatus status) {
        campaignJpaRepository.recordRecurrenceRun(id, lastRunAt, status);
    }

    @Override
    @Transactional
    public void update(long companyId, long id, Campaign row) {
        CampaignEntity entity = campaignJpaRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND, id));
        mapper.applyEditableFields(entity, row);
        campaignJpaRepository.save(entity);
    }

    private Specification<CampaignEntity> buildSpecification(CampaignFilter filter, long companyId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("company").get("id"), companyId));

            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.type() != null) {
                predicates.add(cb.equal(root.get("type"), filter.type()));
            }
            if (filter.aiAgentId() != null) {
                predicates.add(cb.equal(root.get("aiAgentId"), filter.aiAgentId()));
            }
            if (filter.createdBy() != null) {
                predicates.add(cb.equal(root.get("createdBy").get("id"), filter.createdBy()));
            }
            if (filter.recurrenceType() != null) {
                predicates.add(cb.equal(root.get("recurrenceType"), filter.recurrenceType()));
            }
            if (filter.search() != null && !filter.search().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + filter.search().toLowerCase() + "%"));
            }
            if (filter.dateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.dateFrom()));
            }
            if (filter.dateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.dateTo()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
