package uz.murodjon.robotcallv2.billing.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.billing.application.port.output.CallBillingRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.*;
import uz.murodjon.robotcallv2.billing.domain.enums.CallBillingStatus;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.CallBillingEntity;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.mapper.CallBillingEntityMapper;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository.CallBillingJpaRepository;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallAttemptEntity;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallAttemptJpaRepository;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignTargetEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository.CampaignTargetJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class CallBillingRepositoryAdapter implements CallBillingRepository {

    private final CallBillingJpaRepository jpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final CallAttemptJpaRepository callAttemptJpaRepository;
    private final CampaignTargetJpaRepository campaignTargetJpaRepository;
    private final CallBillingEntityMapper mapper;

    public CallBillingRepositoryAdapter(CallBillingJpaRepository jpaRepository,
                                        CompanyJpaRepository companyJpaRepository,
                                        CallAttemptJpaRepository callAttemptJpaRepository,
                                        CampaignTargetJpaRepository campaignTargetJpaRepository,
                                        CallBillingEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.callAttemptJpaRepository = callAttemptJpaRepository;
        this.campaignTargetJpaRepository = campaignTargetJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public CallBilling save(CallBilling callBilling) {
        CompanyEntity company = companyJpaRepository.getReferenceById(callBilling.companyId());
        CallBillingEntity entity = mapper.toCallBillingEntity(callBilling, company,
                callAttemptReference(callBilling.callAttemptId()), targetReference(callBilling.targetId()));
        return mapper.toCallBilling(jpaRepository.save(entity));
    }

    @Override
    public Optional<CallBilling> findByCallAttemptId(long callAttemptId) {
        return jpaRepository.findByCallAttemptId(callAttemptId).map(mapper::toCallBilling);
    }

    @Override
    public void addSummaryCharge(long callAttemptId, CallUsage summaryUsage, CallCostBreakdown cost) {
        jpaRepository.addSummaryCharge(callAttemptId, summaryUsage.promptTokens(), summaryUsage.completionTokens(),
                summaryUsage.cachedTokens(), cost.llmUzs(), cost.llmPromptUzs(), cost.llmCompletionUzs(),
                cost.llmCachedUzs(), cost.totalUzs());
    }

    @Override
    public void addOperatorAnalysisCharge(long callAttemptId, CallUsage analysisUsage, CallCostBreakdown cost) {
        jpaRepository.addOperatorAnalysisCharge(callAttemptId, analysisUsage.promptTokens(),
                analysisUsage.completionTokens(), analysisUsage.cachedTokens(), analysisUsage.extraSttSeconds(),
                cost.llmUzs(), cost.llmPromptUzs(), cost.llmCompletionUzs(), cost.llmCachedUzs(),
                cost.sttUzs(), cost.totalUzs());
    }

    @Override
    public Optional<CallBilling> findOpenHold(long companyId, long targetId) {
        return jpaRepository
                .findFirstByCompanyIdAndTargetIdAndStatusOrderByCreatedAtAsc(companyId, targetId, CallBillingStatus.RESERVED)
                .map(mapper::toCallBilling);
    }

    @Override
    public List<MonthlySpend> findMonthlySpend(long companyId, int limit) {
        return jpaRepository.findMonthlySpend(companyId, limit).stream()
                .map(row -> new MonthlySpend((String) row[0], number(row[1]), number(row[2])))
                .toList();
    }

    @Override
    public List<VariantSpend> findSpendByVariant(long companyId, long campaignId) {
        return jpaRepository.findSpendByVariant(companyId, campaignId).stream()
                .map(row -> new VariantSpend(
                        row[0] != null ? ((Number) row[0]).longValue() : null,
                        number(row[1]),
                        number(row[2])))
                .toList();
    }

    @Override
    public long sumSpentUzsByCampaign(long companyId, long campaignId, Instant since) {
        return jpaRepository.sumSpentUzsByCampaign(companyId, campaignId, since);
    }

    @Override
    public PeriodUsage findUsageSince(long companyId, Instant from) {
        List<Object[]> rows = jpaRepository.findUsageSince(companyId, from);
        if (rows.isEmpty()) {
            return PeriodUsage.NONE;
        }
        Object[] row = rows.getFirst();
        return new PeriodUsage(number(row[0]), number(row[1]), number(row[2]), number(row[3]));
    }

    @Override
    public CostTotals findCostTotals(long companyId, Instant from, Instant to) {
        List<Object[]> rows = jpaRepository.findCostTotals(companyId, from, to);
        if (rows.isEmpty()) {
            return CostTotals.NONE;
        }
        Object[] row = rows.getFirst();
        return new CostTotals(number(row[0]), number(row[1]), number(row[3]), number(row[2]),
                number(row[4]), number(row[5]), number(row[6]), number(row[7]),
                number(row[8]), number(row[9]), number(row[10]), number(row[11]),
                number(row[12]), number(row[13]), number(row[14]), number(row[15]),
                number(row[16]), number(row[17]));
    }

    @Override
    public List<DailySpend> findDailySpend(long companyId, Instant from, Instant to) {
        return jpaRepository.findDailySpend(companyId, from, to).stream()
                .map(row -> new DailySpend(localDate(row[0]), number(row[1])))
                .toList();
    }

    /** A native {@code date} column arrives as java.sql.Date or LocalDate depending on the driver path. */
    private static LocalDate localDate(Object value) {
        return value instanceof java.sql.Date date ? date.toLocalDate() : (LocalDate) value;
    }

    /** SUM over a bigint answers as BigInteger, over an int as Long — both arrive here. */
    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    /** Null until the attempt row exists; billing is written from the call's own thread. */
    private CallAttemptEntity callAttemptReference(Long id) {
        return id != null ? callAttemptJpaRepository.getReferenceById(id) : null;
    }

    /** Null for a call that was not dialled from a campaign target. */
    private CampaignTargetEntity targetReference(Long id) {
        return id != null ? campaignTargetJpaRepository.getReferenceById(id) : null;
    }
}
