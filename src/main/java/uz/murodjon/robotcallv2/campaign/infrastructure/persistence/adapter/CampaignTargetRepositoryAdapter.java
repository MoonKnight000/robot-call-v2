package uz.murodjon.robotcallv2.campaign.infrastructure.persistence.adapter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignTargetRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTargetStats;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetFilter;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetScore;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignTargetEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.mapper.CampaignTargetEntityMapper;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository.CampaignTargetJpaRepository;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository.CampaignTargetSummaryProjection;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.sql.Types;
import java.time.Instant;
import java.util.*;

@Component
public class CampaignTargetRepositoryAdapter implements CampaignTargetRepository {

    private final CampaignTargetJpaRepository campaignTargetJpaRepository;
    private final JdbcTemplate jdbcTemplate;
    private final CampaignTargetEntityMapper mapper;

    public CampaignTargetRepositoryAdapter(CampaignTargetJpaRepository campaignTargetJpaRepository,
                                         JdbcTemplate jdbcTemplate,
                                         CampaignTargetEntityMapper mapper) {
        this.campaignTargetJpaRepository = campaignTargetJpaRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.mapper = mapper;
    }

    /** The statuses the unique index {@code idx_campaign_target_queued_phone} covers. */
    private static final List<TargetStatus> QUEUED = List.of(TargetStatus.PENDING, TargetStatus.IN_PROGRESS);

    /**
     * Rows per INSERT: nine bind parameters each keeps a statement far below PostgreSQL's
     * 32 767, and a 10 000-row import is still only 20 round trips.
     */
    private static final int INSERT_BATCH_SIZE = 500;

    /**
     * Native rather than saveAll: the id is IDENTITY, which turns Hibernate's insert batching
     * off, so saveAll would still be one INSERT per row. ON CONFLICT names no target — the
     * only unique index a new PENDING row can hit is idx_campaign_target_queued_phone — so a
     * number queued meanwhile is skipped instead of failing the statement. client_id is
     * written as given: the column has no foreign key, it also carries a CRM lead id.
     */
    private static final String INSERT_QUEUED_PREFIX = "INSERT INTO campaign_target (company_id, campaign_id, "
            + "client_id, phone, language, context_data, status, attempts, do_not_call, created_at) VALUES ";
    private static final String INSERT_QUEUED_ROW = "(?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, now())";
    private static final String INSERT_QUEUED_SUFFIX = " ON CONFLICT DO NOTHING RETURNING id, phone";

    @Override
    public long add(long companyId, CampaignTarget target) {
        // One statement: ON CONFLICT answers "already queued" without a lookup first.
        Long id = insertAllQueued(companyId, List.of(target)).get(target.phone());
        return id != null ? id : 0;
    }

    @Override
    public Map<String, Long> insertAllQueued(long companyId, List<CampaignTarget> targets) {
        Map<String, Long> idsByPhone = new HashMap<>();
        for (int from = 0; from < targets.size(); from += INSERT_BATCH_SIZE) {
            List<CampaignTarget> batch = targets.subList(from, Math.min(from + INSERT_BATCH_SIZE, targets.size()));
            String sql = INSERT_QUEUED_PREFIX + String.join(", ", Collections.nCopies(batch.size(), INSERT_QUEUED_ROW))
                    + INSERT_QUEUED_SUFFIX;
            List<Object> arguments = new ArrayList<>(batch.size() * 9);
            for (CampaignTarget target : batch) {
                arguments.add(companyId);
                arguments.add(target.campaignId());
                arguments.add(target.clientId());
                arguments.add(target.phone());
                // Typed, so a null language does not make the driver ask the server for the column type.
                arguments.add(new SqlParameterValue(Types.VARCHAR, target.language()));
                arguments.add(target.contextData() != null ? target.contextData() : "{}");
                arguments.add(target.status().name());
                arguments.add(target.attempts());
                arguments.add(target.doNotCall());
            }
            jdbcTemplate.query(sql,
                    (RowCallbackHandler) row -> idsByPhone.put(row.getString("phone"), row.getLong("id")),
                    arguments.toArray());
        }
        return idsByPhone;
    }

    @Override
    public CampaignTarget findQueued(long companyId, long campaignId, String phone) {
        return campaignTargetJpaRepository.findByCampaignIdAndPhoneAndStatusIn(companyId, campaignId, phone, QUEUED)
                .stream().findFirst().map(mapper::toCampaignTarget).orElse(null);
    }

    @Override
    public CampaignTarget find(long companyId, long id) {
        return campaignTargetJpaRepository.findByIdAndCompanyId(id, companyId).map(mapper::toCampaignTarget).orElse(null);
    }

    @Override
    public void resetTargetsForRecurrence(long campaignId) {
        campaignTargetJpaRepository.resetTargetsForRecurrence(campaignId);
    }

    @Override
    public List<CampaignTarget> findByCampaign(long companyId, long campaignId, TargetFilter filter) {
        return campaignTargetJpaRepository.findByCampaignIdAndCompanyId(campaignId, companyId, filter.pageable()).stream()
                .map(mapper::toCampaignTarget)
                .toList();
    }

    @Override
    public long countByCampaign(long companyId, long campaignId) {
        return campaignTargetJpaRepository.countByCampaignIdAndCompanyId(campaignId, companyId);
    }

    @Override
    public long countActive(long campaignId) {
        return campaignTargetJpaRepository.countByCampaignIdAndStatusIn(campaignId, List.of(TargetStatus.PENDING, TargetStatus.IN_PROGRESS));
    }

    @Override
    // Its own short transaction: the UPDATE … FOR UPDATE SKIP LOCKED holds the claimed rows'
    // locks only for this statement, and a read-only transaction around a caller can never
    // turn it into a rejected write.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<CampaignTarget> claimDue(long campaignId, int limit) {
        return campaignTargetJpaRepository.claimDue(campaignId, limit).stream().map(mapper::toCampaignTarget).toList();
    }

    @Override
    // Its own short transaction, for the same reason as claimDue.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<CampaignTarget> claimDueByScore(long campaignId, int limit, int localHour) {
        return campaignTargetJpaRepository.claimDueByScore(campaignId, limit, localHour,
                        TargetScore.BEST_HOUR_BONUS, TargetScore.NEUTRAL).stream()
                .map(mapper::toCampaignTarget).toList();
    }

    @Override
    public List<CampaignTarget> findToScore(long campaignId, Instant scoredBefore, int limit) {
        return campaignTargetJpaRepository.findToScore(campaignId, scoredBefore, limit).stream()
                .map(mapper::toCampaignTarget).toList();
    }

    @Override
    @Transactional
    public void updateScores(long companyId, Map<Long, TargetScore> scores) {
        scores.forEach((id, score) ->
                campaignTargetJpaRepository.updateScore(companyId, id, score.score(), score.bestCallHour()));
    }

    @Override
    // Its own short transaction, for the same reason as claimDue.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CampaignTarget claimNextForOperator(long campaignId, long userId) {
        return campaignTargetJpaRepository.claimNextForOperator(campaignId, userId,
                        (int) CampaignTarget.OPERATOR_CLAIM_LEASE.toMinutes()).stream()
                .map(mapper::toCampaignTarget).findFirst().orElse(null);
    }

    @Override
    public CampaignTarget findHeldByOperator(long campaignId, long userId) {
        return campaignTargetJpaRepository.findHeldByOperator(campaignId, userId).stream()
                .map(mapper::toCampaignTarget).findFirst().orElse(null);
    }

    @Override
    public Map<Long, Long> findCompanyIdsOfExpiredClaims(Instant claimedBefore) {
        Map<Long, Long> companyIdByTargetId = new HashMap<>();
        for (CampaignTargetEntity target
                : campaignTargetJpaRepository.findByStatusAndNextAttemptAtBefore(TargetStatus.IN_PROGRESS, claimedBefore)) {
            companyIdByTargetId.put(target.getId(), target.getCompany().getId());
        }
        return companyIdByTargetId;
    }

    @Override
    public int clearSchedule(long campaignId) {
        return campaignTargetJpaRepository.clearSchedule(campaignId);
    }

    @Override
    public void updateStatus(long id, TargetStatus status, Instant nextAttemptAt) {
        campaignTargetJpaRepository.updateStatus(id, status, nextAttemptAt);
    }

    @Override
    public void moveToStep(long id, String stepId, Instant nextAttemptAt) {
        // Never a null bound into the native query: "at once" is now.
        campaignTargetJpaRepository.moveToStep(id, stepId, nextAttemptAt != null ? nextAttemptAt : Instant.now());
    }

    @Override
    public boolean redialOnce(long id) {
        return campaignTargetJpaRepository.redialOnce(id) > 0;
    }

    @Override
    public void recordOutcome(long id, String outcome, boolean converted) {
        campaignTargetJpaRepository.recordOutcome(id, outcome, converted);
    }

    @Override
    public void releaseClaim(long id, Instant nextAttemptAt) {
        campaignTargetJpaRepository.releaseClaim(id, nextAttemptAt);
    }

    @Override
    public void setDoNotCall(long companyId, long id) {
        campaignTargetJpaRepository.setDoNotCall(id, companyId);
    }

    @Override
    public int deleteUndialedByCampaignId(long companyId, long campaignId) {
        return campaignTargetJpaRepository.deleteUndialedByCampaignId(campaignId, companyId);
    }

    @Override
    public int exhaustPendingByCampaignId(long companyId, long campaignId) {
        return campaignTargetJpaRepository.exhaustPendingByCampaignId(campaignId, companyId);
    }

    @Override
    public Set<String> findQueuedPhones(long companyId, long campaignId) {
        return new HashSet<>(campaignTargetJpaRepository.findPhonesByCampaignIdAndStatusIn(companyId, campaignId, QUEUED));
    }

    @Override
    public Map<Long, CampaignTargetStats> findStatsByCampaignIds(long companyId, Collection<Long> campaignIds) {
        if (campaignIds == null || campaignIds.isEmpty()) {
            return Map.of();
        }
        List<CampaignTargetSummaryProjection> summaries =
                campaignTargetJpaRepository.summarizeByCampaignIdsAndCompanyId(campaignIds, companyId);
        Map<Long, Long> successful = countSuccessfulTargets(companyId, campaignIds);
        Map<Long, CampaignTargetStats> result = new HashMap<>();
        for (CampaignTargetSummaryProjection s : summaries) {
            if (s.getCampaignId() != null) {
                result.put(s.getCampaignId(), new CampaignTargetStats(
                        s.getTotalTargets(),
                        s.getCalledTargets(),
                        s.getPendingTargets(),
                        s.getCompletedTargets(),
                        successful.getOrDefault(s.getCampaignId(), 0L)
                ));
            }
        }
        return result;
    }

    @Override
    public CampaignTargetStats findStatsByCampaignId(long companyId, long campaignId) {
        long successful = countSuccessfulTargets(companyId, List.of(campaignId)).getOrDefault(campaignId, 0L);
        return campaignTargetJpaRepository.summarizeByCampaignIdAndCompanyId(campaignId, companyId)
                .map(s -> new CampaignTargetStats(
                        s.getTotalTargets(),
                        s.getCalledTargets(),
                        s.getPendingTargets(),
                        s.getCompletedTargets(),
                        successful
                ))
                .orElse(CampaignTargetStats.ZERO);
    }

    /** Campaign id → targets with at least one call that reached what the campaign was after. */
    private Map<Long, Long> countSuccessfulTargets(long companyId, Collection<Long> campaignIds) {
        List<String> conversions = Arrays.stream(Disposition.values())
                .filter(Disposition::isConversion)
                .map(Disposition::name)
                .toList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : campaignTargetJpaRepository.countSuccessfulByCampaignIds(campaignIds, companyId, conversions)) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }
}
