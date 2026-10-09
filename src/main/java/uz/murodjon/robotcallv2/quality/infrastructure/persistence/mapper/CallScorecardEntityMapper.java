package uz.murodjon.robotcallv2.quality.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.quality.domain.entity.CallScorecard;
import uz.murodjon.robotcallv2.quality.domain.entity.CallScorecardAnswer;
import uz.murodjon.robotcallv2.quality.infrastructure.persistence.entity.CallScorecardAnswerEntity;
import uz.murodjon.robotcallv2.quality.infrastructure.persistence.entity.CallScorecardEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CallScorecardEntityMapper {

    public CallScorecard toCallScorecard(CallScorecardEntity entity) {
        if (entity == null) {
            return null;
        }
        return new CallScorecard(entity.getCallId(), entity.getCompanyId(), entity.getOperatorUserId(),
                entity.getScore(), entity.getJudgedScore(), entity.getSentiment(), List.copyOf(entity.getTopics()),
                entity.isNeedsReview(),
                entity.getReviewStatus(), entity.getReviewNote(), entity.getReviewedBy(), entity.getReviewedAt(),
                entity.getAnswers().stream().map(CallScorecardEntityMapper::toAnswer).toList(),
                entity.getPromptTokens(), entity.getCompletionTokens(), entity.getCachedTokens(),
                entity.getCreatedAt());
    }

    /** Everything a re-score replaces: the judge's verdict, the whole set of answers, and any earlier review. */
    public void applyEditableFields(CallScorecardEntity entity, CallScorecard scorecard) {
        entity.setOperatorUserId(scorecard.operatorUserId());
        entity.setScore(scorecard.score());
        entity.setJudgedScore(scorecard.judgedScore());
        entity.setSentiment(scorecard.sentiment());
        entity.getTopics().clear();
        entity.getTopics().addAll(scorecard.topics());
        entity.setNeedsReview(scorecard.needsReview());
        applyReviewFields(entity, scorecard);
        entity.setPromptTokens(scorecard.promptTokens());
        entity.setCompletionTokens(scorecard.completionTokens());
        entity.setCachedTokens(scorecard.cachedTokens());
        entity.getAnswers().clear();
        for (CallScorecardAnswer answer : scorecard.answers()) {
            CallScorecardAnswerEntity row = new CallScorecardAnswerEntity();
            row.setScorecard(entity);
            row.setCriterionId(answer.criterionId());
            row.setCriterionName(answer.criterionName());
            row.setWeight(answer.weight());
            row.setAnswer(answer.answer());
            row.setJudgedAnswer(answer.judgedAnswer());
            row.setEvidence(answer.evidence());
            entity.getAnswers().add(row);
        }
    }

    /** What a review changes: the score, the review fields, and each stored answer's current value. */
    public void applyReview(CallScorecardEntity entity, CallScorecard scorecard) {
        entity.setScore(scorecard.score());
        applyReviewFields(entity, scorecard);
        Map<Long, CallScorecardAnswer> byId = new HashMap<>();
        for (CallScorecardAnswer answer : scorecard.answers()) {
            byId.put(answer.id(), answer);
        }
        for (CallScorecardAnswerEntity row : entity.getAnswers()) {
            CallScorecardAnswer answer = byId.get(row.getId());
            if (answer != null) {
                row.setAnswer(answer.answer());
            }
        }
    }

    private static void applyReviewFields(CallScorecardEntity entity, CallScorecard scorecard) {
        entity.setReviewStatus(scorecard.reviewStatus());
        entity.setReviewNote(scorecard.reviewNote());
        entity.setReviewedBy(scorecard.reviewedBy());
        entity.setReviewedAt(scorecard.reviewedAt());
    }

    private static CallScorecardAnswer toAnswer(CallScorecardAnswerEntity row) {
        return new CallScorecardAnswer(row.getId(), row.getCriterionId(), row.getCriterionName(), row.getWeight(),
                row.getAnswer(), row.getJudgedAnswer(), row.getEvidence());
    }
}
