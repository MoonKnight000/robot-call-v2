package uz.murodjon.robotcallv2.campaign.infrastructure.persistence.adapter;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentEntity;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository.AiAgentJpaRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignFilter;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.mapper.CampaignEntityMapper;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository.CampaignJpaRepository;
import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.shared.util.LikePatterns;
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
    private final AiAgentJpaRepository aiAgentJpaRepository;
    private final CampaignEntityMapper mapper;

    public CampaignRepositoryAdapter(CampaignJpaRepository campaignJpaRepository,
                                     CompanyJpaRepository companyJpaRepository,
                                     UserJpaRepository userJpaRepository,
                                     AiAgentJpaRepository aiAgentJpaRepository,
                                     CampaignEntityMapper mapper) {
        this.campaignJpaRepository = campaignJpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.userJpaRepository = userJpaRepository;
        this.aiAgentJpaRepository = aiAgentJpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public long create(long companyId, Campaign row) {
        // References, not lookups: the service has already checked the agent is this
        // company's, and the company comes from the caller's token — deciding "not found"
        // is the service's job, not the adapter's.
        CompanyEntity company = companyJpaRepository.getReferenceById(companyId);
        UserEntity createdBy = row.createdBy() != null
                ? userJpaRepository.findById(row.createdBy()).orElse(null)
                : null;

        CampaignEntity entity = mapper.toCampaignEntity(row, company, createdBy, aiAgentReference(row.aiAgentId()));
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
    public long countByCompanyId(long companyId) {
        return campaignJpaRepository.countByCompanyId(companyId);
    }

    @Override
    public long countByFilter(long companyId, CampaignFilter filter) {
        return campaignJpaRepository.count(buildSpecification(filter, companyId));
    }

    @Override
    public List<Campaign> searchByName(long companyId, String q, int limit) {
        return campaignJpaRepository.searchByCompanyId(companyId, "%" + LikePatterns.escape(q.toLowerCase()) + "%",
                        PageRequest.of(0, limit))
                .stream()
                .map(mapper::toCampaign)
                .toList();
    }

    @Override
    public List<Campaign> findActive() {
        return campaignJpaRepository.findByStatusAndCompany_StatusOrderById(CampaignStatus.ACTIVE, CompanyStatus.ACTIVE).stream()
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
    public List<Campaign> findScheduledDue(Instant now) {
        return campaignJpaRepository.findByStatusAndStartAtLessThanEqual(CampaignStatus.SCHEDULED, now).stream()
                .map(mapper::toCampaign)
                .toList();
    }

    @Override
    public List<Campaign> findActiveExpired(Instant now) {
        return campaignJpaRepository.findByStatusAndEndAtLessThanEqual(CampaignStatus.ACTIVE, now).stream()
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
    public boolean completeIfFinished(long companyId, long id) {
        return campaignJpaRepository.completeIfFinished(id, companyId) == 1;
    }

    @Override
    @Transactional
    public void recordRecurrenceRun(long id, Instant lastRunAt, CampaignStatus status) {
        campaignJpaRepository.recordRecurrenceRun(id, lastRunAt, status);
    }

    @Override
    @Transactional
    public boolean update(long companyId, long id, Campaign row) {
        CampaignEntity entity = campaignJpaRepository.findByIdAndCompanyId(id, companyId).orElse(null);
        if (entity == null) {
            // The service loads the campaign first and answers 404 itself; a row that
            // vanished in between is left alone.
            return true;
        }
        if (entity.getVersion() != row.version()) {
            return false;
        }
        // An edit landing between this read and the commit is caught by @Version at flush.
        mapper.applyEditableFields(entity, row, aiAgentReference(row.aiAgentId()));
        campaignJpaRepository.save(entity);
        return true;
    }

    private AiAgentEntity aiAgentReference(Long aiAgentId) {
        return aiAgentId != null ? aiAgentJpaRepository.getReferenceById(aiAgentId) : null;
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
                predicates.add(cb.equal(root.get("aiAgent").get("id"), filter.aiAgentId()));
            }
            if (filter.createdBy() != null) {
                predicates.add(cb.equal(root.get("createdBy").get("id"), filter.createdBy()));
            }
            if (filter.recurrenceType() != null) {
                predicates.add(cb.equal(root.get("recurrenceType"), filter.recurrenceType()));
            }
            if (filter.search() != null && !filter.search().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        LikePatterns.buildContainsPattern(filter.search().toLowerCase()), LikePatterns.ESCAPE));
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
