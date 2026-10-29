package uz.murodjon.robotcallv2.quality.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.metrics.CallMetrics;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;

import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.quality.application.dto.ReviewScorecardRequest;
import uz.murodjon.robotcallv2.quality.application.dto.SaveScorecardCriterionRequest;
import uz.murodjon.robotcallv2.quality.application.dto.ScorecardAnswerCorrection;
import uz.murodjon.robotcallv2.quality.application.port.input.ScorecardUseCase;
import uz.murodjon.robotcallv2.quality.application.port.output.CallScorecardRepository;
import uz.murodjon.robotcallv2.quality.application.port.output.ScorecardCriterionRepository;
import uz.murodjon.robotcallv2.quality.application.port.output.ScorecardJudgePort;
import uz.murodjon.robotcallv2.quality.domain.entity.*;
import uz.murodjon.robotcallv2.quality.domain.enums.CriterionAnswer;
import uz.murodjon.robotcallv2.quality.domain.enums.ScorecardReviewStatus;
import uz.murodjon.robotcallv2.quality.domain.service.ScoreCalculator;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.role.domain.enums.Feature;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.dialog.Sentiment;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Operator call scorecards (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md §2.3): the company's
 * criteria, and each finished operator call marked on them by the judge.
 */
@Service
public class ScorecardService implements ScorecardUseCase {

    private static final Logger log = LoggerFactory.getLogger(ScorecardService.class);

    private final ScorecardCriterionRepository scorecardCriterionRepository;
    private final CallScorecardRepository callScorecardRepository;
    private final ScorecardJudgePort scorecardJudgePort;
    private final AuditUseCase auditUseCase;
    private final CompanyConfigUseCase companyConfigUseCase;
    private final CompanyUseCase companyUseCase;
    private final NotificationUseCase notificationUseCase;
    private final CurrentUser currentUser;
    private final ObjectProvider<CallMetrics> callMetricsProvider;

    public ScorecardService(ScorecardCriterionRepository scorecardCriterionRepository,
                            CallScorecardRepository callScorecardRepository, ScorecardJudgePort scorecardJudgePort,
                            AuditUseCase auditUseCase, CompanyConfigUseCase companyConfigUseCase, CompanyUseCase companyUseCase,
                            NotificationUseCase notificationUseCase, CurrentUser currentUser) {
        this(scorecardCriterionRepository, callScorecardRepository, scorecardJudgePort, auditUseCase,
                companyConfigUseCase, companyUseCase, notificationUseCase, currentUser, null);
    }

    @Autowired
    public ScorecardService(ScorecardCriterionRepository scorecardCriterionRepository,
                            CallScorecardRepository callScorecardRepository, ScorecardJudgePort scorecardJudgePort,
                            AuditUseCase auditUseCase, CompanyConfigUseCase companyConfigUseCase, CompanyUseCase companyUseCase,
                            NotificationUseCase notificationUseCase, CurrentUser currentUser,
                            ObjectProvider<CallMetrics> callMetricsProvider) {
        this.scorecardCriterionRepository = scorecardCriterionRepository;
        this.callScorecardRepository = callScorecardRepository;
        this.scorecardJudgePort = scorecardJudgePort;
        this.auditUseCase = auditUseCase;
        this.companyConfigUseCase = companyConfigUseCase;
        this.companyUseCase = companyUseCase;
        this.notificationUseCase = notificationUseCase;
        this.currentUser = currentUser;
        this.callMetricsProvider = callMetricsProvider;
    }

    @Override
    public ScorecardCriterion createCriterion(long companyId, SaveScorecardCriterionRequest request) {
        ScorecardCriterion saved = scorecardCriterionRepository.create(companyId, toCriterion(request));
        auditUseCase.record(companyId, "SCORECARD_CRITERION_CREATE", "scorecard_criterion",
                String.valueOf(saved.id()), saved.name());
        return saved;
    }

    @Override
    public ScorecardCriterion updateCriterion(long companyId, long id, SaveScorecardCriterionRequest request) {
        requireCriterion(companyId, id);
        ScorecardCriterion saved = scorecardCriterionRepository.update(companyId, id, toCriterion(request));
        auditUseCase.record(companyId, "SCORECARD_CRITERION_UPDATE", "scorecard_criterion", String.valueOf(id),
                saved.name());
        return saved;
    }

    /** Calls already scored keep the criterion's name and weight — they were copied in. */
    @Override
    public void deleteCriterion(long companyId, long id) {
        requireCriterion(companyId, id);
        scorecardCriterionRepository.delete(companyId, id);
        auditUseCase.record(companyId, "SCORECARD_CRITERION_DELETE", "scorecard_criterion", String.valueOf(id), null);
    }

    @Override
    public ScorecardCriterion requireCriterion(long companyId, long id) {
        ScorecardCriterion criterion = scorecardCriterionRepository.find(companyId, id);
        if (criterion == null) {
            throw new NotFoundException(ErrorCode.SCORECARD_CRITERION_NOT_FOUND, id);
        }
        return criterion;
    }

    @Override
    public PageableData<ScorecardCriterion> listCriteria(long companyId, ScorecardCriterionFilter filter) {
        return PageableData.of(scorecardCriterionRepository.findAll(companyId, filter), filter.pageOrDefault(),
                filter.sizeOrDefault(), scorecardCriterionRepository.count(companyId, filter));
    }

    @Override
    public CallScorecard requireCallScorecard(long companyId, long callId) {
        CallScorecard scorecard = callScorecardRepository.findByCompanyIdAndCallId(companyId, callId);
        if (scorecard == null) {
            throw new NotFoundException(ErrorCode.CALL_SCORECARD_NOT_FOUND, callId);
        }
        return scorecard;
    }

    @Override
    public PageableData<CallScorecard> filterCallScorecards(long companyId, CallScorecardFilter filter) {
        return callScorecardRepository.findPageByCompanyId(companyId, filter);
    }

    /**
     * Applies the corrections by answer id and re-scores the call; the judge's answers and score
     * stay beside them. CORRECTED when any answer now differs from the judge's, CONFIRMED
     * otherwise — a later review replaces an earlier one.
     */
    @Override
    public CallScorecard recordReview(long companyId, long callId, ReviewScorecardRequest request) {
        CallScorecard scorecard = requireCallScorecard(companyId, callId);
        Map<Long, CriterionAnswer> corrections = new HashMap<>();
        for (ScorecardAnswerCorrection correction : request.correctionsOrEmpty()) {
            corrections.put(correction.answerId(), correction.answer());
        }
        List<CallScorecardAnswer> answers = new ArrayList<>();
        for (CallScorecardAnswer answer : scorecard.answers()) {
            CriterionAnswer corrected = corrections.remove(answer.id());
            answers.add(corrected == null ? answer : answer.withAnswer(corrected));
        }
        if (!corrections.isEmpty()) {
            throw new ValidationException(ErrorCode.SCORECARD_ANSWER_UNKNOWN, corrections.keySet().iterator().next(),
                    callId);
        }
        boolean corrected = answers.stream().anyMatch(answer -> answer.answer() != answer.judgedAnswer());
        ScorecardReviewStatus status = corrected ? ScorecardReviewStatus.CORRECTED : ScorecardReviewStatus.CONFIRMED;
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        CallScorecard saved = callScorecardRepository.updateReview(companyId, scorecard.withReview(answers,
                ScoreCalculator.calculate(answers), status, note, currentUser.id().orElse(null), Instant.now()));
        auditUseCase.record(companyId, "SCORECARD_REVIEW", "call", String.valueOf(callId), status.name());
        CallMetrics metrics = callMetricsProvider != null ? callMetricsProvider.getIfAvailable() : null;
        if (metrics != null) {
            metrics.scorecardReviewed(status.name());
        }
        return saved;
    }

    @Override
    public CallScorecard recordCallScorecard(long companyId, long callId, Long operatorUserId, String transcript) {
        if (!companyUseCase.isFeatureAvailable(companyId, Feature.QUALITY_ASSURANCE)) {
            return null;
        }
        if (transcript == null || transcript.isBlank()) {
            return null;
        }
        try {
            List<ScorecardCriterion> criteria = scorecardCriterionRepository.findEnabledByCompanyId(companyId);
            if (criteria.isEmpty()) {
                return null;
            }
            ScorecardVerdict verdict = scorecardJudgePort.findVerdict(criteria, transcript);
            if (verdict == null) {
                return null;
            }
            Integer score = ScoreCalculator.calculate(verdict.answers());
            boolean needsReview = needsReview(companyId, score, verdict.sentiment());
            CallScorecard saved = callScorecardRepository.upsert(companyId,
                    CallScorecard.judged(callId, operatorUserId, score, needsReview, verdict));
            if (score != null) {
                CallMetrics callMetrics = callMetricsProvider != null ? callMetricsProvider.getIfAvailable() : null;
                if (callMetrics != null) {
                    callMetrics.recordScorecardScore(score);
                    if (needsReview) {
                        callMetrics.scorecardReviewNeeded();
                    }
                }
            }
            if (needsReview) {
                notifyReview(companyId, callId, score, verdict.sentiment());
            }
            return saved;
        } catch (Exception e) {
            log.warn("[{}] operator call scorecard failed: {}", callId, e.getMessage());
            return null;
        }
    }

    @Override
    public int deleteEvidenceWithoutTranscript(int batchSize) {
        return callScorecardRepository.deleteEvidenceWithoutTranscript(batchSize);
    }

    /** Under the company's threshold, or a client the judge read as upset. */
    private boolean needsReview(long companyId, Integer score, Sentiment sentiment) {
        if (sentiment == Sentiment.NEGATIVE || sentiment == Sentiment.HOSTILE) {
            return true;
        }
        CompanyConfig config = companyConfigUseCase.find(companyId);
        int threshold = config != null ? config.scorecardReviewThreshold()
                : CompanyConfig.DEFAULT_SCORECARD_REVIEW_THRESHOLD;
        return score != null && score < threshold;
    }

    private void notifyReview(long companyId, long callId, Integer score, Sentiment sentiment) {
        try {
            String reason = score == null ? "mijoz kayfiyati: " + sentiment
                    : "ball: " + score + (sentiment == null ? "" : ", mijoz kayfiyati: " + sentiment);
            notificationUseCase.notify(companyId, NotificationType.SCORECARD_REVIEW, "Qo'ng'iroq ko'rib chiqilishi kerak",
                    "Operator qo'ng'irog'i (ID: " + callId + ") tekshiruvga tushdi — " + reason, null);
        } catch (Exception e) {
            log.warn("[{}] scorecard review notification failed: {}", callId, e.getMessage());
        }
    }

    private static ScorecardCriterion toCriterion(SaveScorecardCriterionRequest request) {
        return ScorecardCriterion.of(request.name().trim(), request.instruction().trim(), request.weight(),
                request.enabledOrDefault());
    }
}
