package uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallTalkMetrics;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallAttemptEntity;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallResultEntity;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallTalkMetricsEntity;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallTechnicalEntity;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallAttemptJpaRepository;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallResultJpaRepository;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallTalkMetricsJpaRepository;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallTechnicalJpaRepository;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallTranscriptJpaRepository;
import uz.murodjon.robotcallv2.report.application.port.output.CallReportRepository;
import uz.murodjon.robotcallv2.report.domain.entity.*;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.util.LikePatterns;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter.ReportSql.*;
//todo clean code
/**
 * The call list, one call in full, and a number's history. Native SQL through
 * {@link JdbcClient}, every column read by its alias.
 */
@Component
public class CallReportRepositoryAdapter implements CallReportRepository {

    /** One call as a report line: the attempt, its target and campaign, its result and operator. */
    private static final String CALL_SELECT = """
            SELECT a.id            AS call_id,
                   a.target_id     AS target_id,
                   -- The attempt's own number since V4; campaign_target is the fallback
                   -- for rows written before it. A manual or inbound call has no target.
                   COALESCE(a.phone, t.phone) AS phone,
                   a.language      AS language,
                   a.started_at    AS started_at,
                   a.ended_at      AS ended_at,
                   a.duration_sec  AS duration_sec,
                   a.disposition   AS disposition,
                   a.hangup_cause  AS hangup_cause,
                   a.recording_file_id AS recording_file_id,
                   r.summary       AS summary,
                   r.promised_date AS promised_date,
                   r.promised_amount AS promised_amount,
                   r.crm_note_id   AS crm_note_id,
                   t.context_data ->> 'clientName' AS client_name,
                   c.name          AS campaign_name,
                   u.name          AS operator_name
            FROM call_attempt a
            LEFT JOIN campaign_target t ON t.id = a.target_id
            LEFT JOIN campaign c ON c.id = t.campaign_id
            LEFT JOIN call_result r ON r.call_id = a.id
            LEFT JOIN app_user u ON u.id = a.operator_user_id
            """;

    /** What the list's total is counted over: the only join its filter reaches. */
    private static final String CALL_COUNT = """
            SELECT count(*) AS n
            FROM call_attempt a
            LEFT JOIN campaign_target t ON t.id = a.target_id
            """;

    private final JdbcClient jdbc;
    private final CallAttemptJpaRepository callAttemptJpaRepository;
    private final CallResultJpaRepository callResultJpaRepository;
    private final CallTranscriptJpaRepository callTranscriptJpaRepository;
    private final CallTechnicalJpaRepository callTechnicalJpaRepository;
    private final CallTalkMetricsJpaRepository callTalkMetricsJpaRepository;

    public CallReportRepositoryAdapter(JdbcTemplate jdbcTemplate,
                                       CallAttemptJpaRepository callAttemptJpaRepository,
                                       CallResultJpaRepository callResultJpaRepository,
                                       CallTranscriptJpaRepository callTranscriptJpaRepository,
                                       CallTechnicalJpaRepository callTechnicalJpaRepository,
                                       CallTalkMetricsJpaRepository callTalkMetricsJpaRepository) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
        this.callAttemptJpaRepository = callAttemptJpaRepository;
        this.callResultJpaRepository = callResultJpaRepository;
        this.callTranscriptJpaRepository = callTranscriptJpaRepository;
        this.callTechnicalJpaRepository = callTechnicalJpaRepository;
        this.callTalkMetricsJpaRepository = callTalkMetricsJpaRepository;
    }


    @Override
    public List<ReportedCall> findCalls(long companyId, Long campaignId, CallFilter filter) {
        return buildCallPage(companyId, campaignId, filter).prepare(jdbc)
                .query(CallReportRepositoryAdapter::toReportedCall)
                .list();
    }

    @Override
    public long countCalls(long companyId, Long campaignId, CallFilter filter) {
        return applyCallFilter(new SqlStatement(CALL_COUNT), companyId, campaignId, filter).prepare(jdbc)
                .query((rs, n) -> rs.getLong("n"))
                .single();
    }

    @Override
    public List<ReportedCall> findCallsForExport(long companyId, CallFilter filter, int limit) {
        return applyCallFilter(new SqlStatement(CALL_SELECT), companyId, null, filter)
                .append(filter.orderByClause())
                .append("LIMIT :limit")
                .bind("limit", limit)
                .prepare(jdbc)
                .query(CallReportRepositoryAdapter::toReportedCall)
                .list();
    }

    /**
     * One page of the list. By page number it is the filter's order with LIMIT/OFFSET. After
     * a cursor it is the calls below that id, newest first, with no OFFSET: the rows before
     * the page are never read, however deep the page is.
     */
    static SqlStatement buildCallPage(long companyId, Long campaignId, CallFilter filter) {
        SqlStatement statement = applyCallFilter(new SqlStatement(CALL_SELECT), companyId, campaignId, filter);
        if (filter.hasCursor()) {
            return statement.where("a.id < :afterId", "afterId", filter.afterId())
                    .append("ORDER BY a.id DESC LIMIT :limit")
                    .bind("limit", filter.sizeOrDefault());
        }
        return statement.append(filter.orderByClause())
                .append("LIMIT :limit OFFSET :offset")
                .bind("limit", filter.sizeOrDefault())
                .bind("offset", filter.offset());
    }

    /**
     * The list's WHERE: the company, the campaign the list is opened on (if any), then the
     * filter's own narrowing — written once, so the page, its count and the export always
     * read the same calls. Explicit {@code ids} replace the filter's narrowing (that is how a
     * selection is exported), never the company or the campaign.
     */
    static SqlStatement applyCallFilter(SqlStatement statement, long companyId, Long campaignId, CallFilter filter) {
        statement.where("a.company_id = :companyId", "companyId", companyId);
        if (campaignId != null) {
            statement.where("t.campaign_id = :scopeCampaignId", "scopeCampaignId", campaignId);
        }
        if (filter.ids() != null && !filter.ids().isEmpty()) {
            return statement.where("a.id IN (:ids)", "ids", filter.ids());
        }
        String phonePattern = LikePatterns.buildContainsPattern(filter.q());
        if (phonePattern != null) {
            statement.where("COALESCE(a.phone, t.phone) LIKE :q ESCAPE '!'", "q", phonePattern);
        }
        if (filter.campaignId() != null) {
            statement.where("t.campaign_id = :campaignId", "campaignId", filter.campaignId());
        }
        if (filter.disposition() != null) {
            statement.where("a.disposition = :disposition", "disposition", filter.disposition().name());
        }
        if (filter.dateFrom() != null) {
            statement.where("a.started_at >= :dateFrom", "dateFrom", toTimestamptz(filter.dateFrom()));
        }
        if (filter.dateTo() != null) {
            statement.where("a.started_at < :dateTo", "dateTo", toTimestamptz(filter.dateTo()));
        }
        if (filter.durationMinSec() != null) {
            statement.where("a.duration_sec >= :durationMinSec", "durationMinSec", filter.durationMinSec());
        }
        if (filter.durationMaxSec() != null) {
            statement.where("a.duration_sec <= :durationMaxSec", "durationMaxSec", filter.durationMaxSec());
        }
        return statement;
    }

    @Override
    public ReportedCall findCall(long companyId, long callId) {
        return jdbc.sql(CALL_SELECT + " WHERE a.id = :callId AND a.company_id = :companyId")
                .param("callId", callId)
                .param("companyId", companyId)
                .query(CallReportRepositoryAdapter::toReportedCall)
                .optional()
                .orElse(null);
    }

    /** One read-only transaction for the call and the three reads that complete it. */
    @Override
    @Transactional(readOnly = true)
    public CallReport findCallReport(long companyId, long callId) {
        ReportedCall call = findCall(companyId, callId);
        if (call == null) {
            return null;
        }

        List<TranscriptLine> transcript = callTranscriptJpaRepository.findByCall_IdOrderBySeq(callId).stream()
                .map(line -> new TranscriptLine(line.getSeq(), line.getRole(), line.getText(), line.getDialogState(),
                        line.getTsOffsetMs(), line.getSttConfidence()))
                .toList();
        CallResultEntity result = callResultJpaRepository.findByCall_Id(callId).orElse(null);
        String errorMessage = callAttemptJpaRepository.findByIdAndCompanyId(callId, companyId)
                .map(CallAttemptEntity::getErrorMessage).orElse(null);
        return new CallReport(
                call,
                transcript,
                result != null ? result.getReasonCode() : null,
                result != null ? result.getSentiment() : null,
                result != null && result.isNeedsFollowUp(),
                result != null ? result.getFollowUpNote() : null,
                result != null && result.isEscalated(),
                errorMessage,
                callTechnicalJpaRepository.findById(callId).map(CallReportRepositoryAdapter::toTechnicalMetrics).orElse(null),
                callTalkMetricsJpaRepository.findById(callId).map(CallReportRepositoryAdapter::toTalkMetrics).orElse(null));
    }

    private static CallTechnicalMetrics toTechnicalMetrics(CallTechnicalEntity entity) {
        return new CallTechnicalMetrics(
                entity.getChannelName(), entity.getTrunk(), entity.getAmdResult(),
                entity.getSttProvider(), entity.getTtsProvider(), entity.getTtsVoice(), entity.getLlmModel(),
                entity.getPromptTokens(), entity.getCompletionTokens(), entity.getCachedTokens(),
                entity.getTurnCount(), entity.getAvgTurnLatencyMs(), entity.getMaxTurnLatencyMs(),
                entity.getAvgLlmLatencyMs(), entity.getMaxLlmLatencyMs(),
                entity.getRtpPacketsReceived(), entity.getRtpPacketsLost(), entity.getRtpJitterMs(),
                entity.getRtpMos());
    }

    private static CallTalkMetrics toTalkMetrics(CallTalkMetricsEntity entity) {
        return new CallTalkMetrics(entity.getId(), entity.getAnalysedMs(), entity.getClientTalkMs(),
                entity.getOperatorTalkMs(), entity.getOvertalkMs(), entity.getSilenceMs(),
                entity.getLongestSilenceMs());
    }

    @Override
    public Long findRecordingFileId(long companyId, long callId) {
        return callAttemptJpaRepository.findRecordingFileIdByIdAndCompanyId(callId, companyId).orElse(null);
    }

    @Override
    public List<PhoneCallHistoryEntry> findCallsByPhone(long companyId, String phone, int limit) {
        return jdbc.sql("SELECT a.id AS call_id, cmp.name AS campaign_name, a.started_at AS started_at, "
                        + "a.duration_sec AS duration_sec, a.disposition AS disposition "
                        + "FROM call_attempt a "
                        + "LEFT JOIN campaign_target t ON t.id = a.target_id "
                        + "LEFT JOIN campaign cmp ON cmp.id = t.campaign_id "
                        + "WHERE COALESCE(a.phone, t.phone) = :phone AND a.company_id = :companyId "
                        + "ORDER BY a.started_at DESC NULLS LAST LIMIT :limit")
                .param("phone", phone)
                .param("companyId", companyId)
                .param("limit", limit)
                .query((rs, n) -> new PhoneCallHistoryEntry(
                        rs.getLong("call_id"), rs.getString("campaign_name"), instantOrNull(rs, "started_at"),
                        intOrNull(rs, "duration_sec"), rs.getString("disposition")))
                .list();
    }

    private static ReportedCall toReportedCall(ResultSet rs, int rowNum) throws SQLException {
        return new ReportedCall(
                rs.getLong("call_id"),
                rs.getLong("target_id"),
                rs.getString("phone"),
                rs.getString("language"),
                instantOrNull(rs, "started_at"),
                instantOrNull(rs, "ended_at"),
                intOrNull(rs, "duration_sec"),
                enumOrNull(Disposition.class, rs.getString("disposition")),
                rs.getString("hangup_cause"),
                rs.getObject("recording_file_id") != null,
                rs.getString("summary"),
                localDateOrNull(rs, "promised_date"),
                rs.getBigDecimal("promised_amount"),
                rs.getString("crm_note_id"),
                rs.getString("client_name"),
                rs.getString("campaign_name"),
                rs.getString("operator_name"));
    }
}
