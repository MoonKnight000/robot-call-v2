package uz.murodjon.robotcallv2.quality.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.quality.domain.entity.CallQuality;
import uz.murodjon.robotcallv2.quality.infrastructure.persistence.entity.CallQualityEntity;

@Component
public class CallQualityEntityMapper {

    public CallQuality toCallQuality(CallQualityEntity entity) {
        if (entity == null) {
            return null;
        }
        return new CallQuality(
                entity.getId(),
                entity.getCompanyId(),
                entity.getCallAttemptId(),
                entity.getAiAgentId(),
                entity.getAgentVersion(),
                entity.getDisposition(),
                entity.getScore(),
                entity.getGuardrailViolation(),
                entity.getLanguageMismatch(),
                entity.getTalkedOverClient(),
                entity.getOutcomeCorrect(),
                entity.getGoalReached(),
                entity.getUnansweredQuestion(),
                entity.getToolMisuse(),
                entity.getNote(),
                entity.isNeedsReview(),
                entity.getTurnCount(),
                entity.getRepairCount(),
                entity.getBargeInCount(),
                entity.getNoInputPromptCount(),
                entity.getLowConfidenceCount(),
                entity.getToolErrorCount(),
                entity.isRepeatCall(),
                entity.getReviewStatus(),
                entity.getReviewReason(),
                entity.getReviewedBy(),
                entity.getReviewedAt(),
                entity.getKnowledgeItemId(),
                entity.getTestCaseId(),
                entity.getCreatedAt()
        );
    }

    public CallQualityEntity toCallQualityEntity(CallQuality domain) {
        CallQualityEntity entity = new CallQualityEntity();
        entity.setId(domain.id());
        entity.setCompanyId(domain.companyId());
        entity.setCallAttemptId(domain.callAttemptId());
        entity.setAiAgentId(domain.aiAgentId());
        entity.setAgentVersion(domain.agentVersion());
        entity.setDisposition(domain.disposition());
        entity.setScore(domain.score());
        entity.setGuardrailViolation(domain.guardrailViolation());
        entity.setLanguageMismatch(domain.languageMismatch());
        entity.setTalkedOverClient(domain.talkedOverClient());
        entity.setOutcomeCorrect(domain.outcomeCorrect());
        entity.setGoalReached(domain.goalReached());
        entity.setUnansweredQuestion(domain.unansweredQuestion());
        entity.setToolMisuse(domain.toolMisuse());
        entity.setNote(domain.note());
        entity.setNeedsReview(domain.needsReview());
        entity.setTurnCount(domain.turnCount());
        entity.setRepairCount(domain.repairCount());
        entity.setBargeInCount(domain.bargeInCount());
        entity.setNoInputPromptCount(domain.noInputPromptCount());
        entity.setLowConfidenceCount(domain.lowConfidenceCount());
        entity.setToolErrorCount(domain.toolErrorCount());
        entity.setRepeatCall(domain.repeatCall());
        entity.setReviewStatus(domain.reviewStatus());
        entity.setReviewReason(domain.reviewReason());
        entity.setReviewedBy(domain.reviewedBy());
        entity.setReviewedAt(domain.reviewedAt());
        entity.setKnowledgeItemId(domain.knowledgeItemId());
        entity.setTestCaseId(domain.testCaseId());
        entity.setCreatedAt(domain.createdAt());
        return entity;
    }
}
