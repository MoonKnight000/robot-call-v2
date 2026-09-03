package uz.murodjon.robotcallv2.campaign.infrastructure.persistence.adapter;

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
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.scenario.infrastructure.persistence.entity.ScenarioEntity;
import uz.murodjon.robotcallv2.scenario.infrastructure.persistence.repository.ScenarioJpaRepository;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.entity.UserEntity;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.repository.UserJpaRepository;

import java.time.Instant;
import java.util.List;

@Component
public class CampaignRepositoryAdapter implements CampaignRepository {

    private final CampaignJpaRepository jpa;
    private final CompanyJpaRepository companyJpa;
    private final ScenarioJpaRepository scenarioJpa;
    private final UserJpaRepository userJpa;
    private final CurrentCompany currentCompany;
    private final CampaignMapper mapper;

    public CampaignRepositoryAdapter(CampaignJpaRepository jpa,
                                     CompanyJpaRepository companyJpa,
                                     ScenarioJpaRepository scenarioJpa,
                                     UserJpaRepository userJpa,
                                     CurrentCompany currentCompany,
                                     CampaignMapper mapper) {
        this.jpa = jpa;
        this.companyJpa = companyJpa;
        this.scenarioJpa = scenarioJpa;
        this.userJpa = userJpa;
        this.currentCompany = currentCompany;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public long create(Campaign row) {
        long companyId = row.companyId() > 0 ? row.companyId() : currentCompany.id();
        CompanyEntity company = companyJpa.findById(companyId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMPANY_NOT_FOUND, companyId));
        ScenarioEntity scenario = scenarioJpa.findById(row.scenarioId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.SCENARIO_NOT_FOUND, row.scenarioId()));
        UserEntity createdBy = row.createdBy() != null ? userJpa.findById(row.createdBy()).orElse(null) : null;

        CampaignEntity entity = mapper.toEntity(row, company, scenario, createdBy);
        entity.setCreatedAt(Instant.now());
        CampaignEntity saved = jpa.save(entity);
        return saved.getId();
    }

    @Override
    public Campaign find(long id) {
        return jpa.findByIdAndCompanyId(id, currentCompany.id())
                .map(mapper::toDomain)
                .orElse(null);
    }

    @Override
    public List<Campaign> findAll(CampaignFilter filter) {
        Specification<CampaignEntity> spec = buildSpecification(filter, currentCompany.id());
        return jpa.findAll(spec, filter.pageable()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public long count() {
        return jpa.countByCompanyId(currentCompany.id());
    }

    @Override
    public long count(CampaignFilter filter) {
        Specification<CampaignEntity> spec = buildSpecification(filter, currentCompany.id());
        return jpa.count(spec);
    }

    @Override
    public List<Campaign> searchByName(String q, int limit) {
        return jpa.searchByCompanyId(currentCompany.id(), "%" + q.toLowerCase() + "%",
                        org.springframework.data.domain.PageRequest.of(0, limit))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Campaign> findActive() {
        return jpa.findByStatusOrderById(CampaignStatus.ACTIVE).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Campaign> findRecurring() {
        return jpa.findByRecurrenceTypeNotAndStatusNot(RecurrenceType.ONCE, CampaignStatus.ARCHIVED).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void updateStatus(long companyId, long id, CampaignStatus status) {
        jpa.updateStatus(id, status, companyId);
    }

    @Override
    @Transactional
    public void recordRecurrenceRun(long id, Instant lastRunAt, CampaignStatus status) {
        jpa.recordRecurrenceRun(id, lastRunAt, status);
    }

    @Override
    @Transactional
    public void update(long id, Campaign row) {
        CampaignEntity entity = jpa.findByIdAndCompanyId(id, currentCompany.id())
                .orElseThrow(() -> new NotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND, id));
        applyEditableFields(entity, row);
        jpa.save(entity);
    }

    private static void applyEditableFields(CampaignEntity entity, Campaign row) {
        entity.setName(row.name());
        entity.setGoalPrompt(row.goalPrompt());
        entity.setDefaultLanguage(row.defaultLanguage());
        entity.setDialWindowStart(row.dialWindowStart());
        entity.setDialWindowEnd(row.dialWindowEnd());
        entity.setDialDays(row.dialDays());
        entity.setMaxAttempts(row.maxAttempts());
        entity.setRetryIntervalMinutes(row.retryIntervalMinutes());
        entity.setMaxConcurrentCalls(row.maxConcurrentCalls());
        entity.setTtsVoice(row.ttsVoice());
        entity.setLanguageVoices(row.languageVoices());
        entity.setSipTrunkIds(row.sipTrunkIds());
        entity.setDailyCallCap(row.dailyCallCap());
        entity.setDisclosureEnabled(row.disclosureEnabled());
        entity.setRecurrenceType(row.recurrenceType());
        entity.setRecurringDayOfMonth(row.recurringDayOfMonth());
        entity.setCronExpression(row.cronExpression());
        entity.setAutoResetTargets(row.autoResetTargets());
        entity.setAmbientSound(row.ambientSound());
        entity.setMidCallSmsEnabled(row.midCallSmsEnabled());
        entity.setMidCallSmsTemplate(row.midCallSmsTemplate());
        entity.setVoicemailAction(row.voicemailAction());
        entity.setVoicemailMessage(row.voicemailMessage());
        entity.setDtmfInputEnabled(row.dtmfInputEnabled());
        entity.setEmotionAdaptiveVoice(row.emotionAdaptiveVoice());
    }

    private Specification<CampaignEntity> buildSpecification(CampaignFilter filter, long companyId) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("company").get("id"), companyId));

            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.type() != null) {
                predicates.add(cb.equal(root.get("type"), filter.type()));
            }
            if (filter.scenarioId() != null) {
                predicates.add(cb.equal(root.get("scenario").get("id"), filter.scenarioId()));
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
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
