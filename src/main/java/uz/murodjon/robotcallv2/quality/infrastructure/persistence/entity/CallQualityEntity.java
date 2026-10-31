package uz.murodjon.robotcallv2.quality.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallAttemptEntity;
import uz.murodjon.robotcallv2.quality.domain.enums.CallReviewStatus;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;

/**
 * JPA entity for {@code call_quality}.
 * AI metrics and automated signals for an AI-agent-handled call attempt.
 * Domain attributes like {@code aiAgentId}, {@code agentVersion}, and {@code disposition}
 * are normalized and resolved directly from the parent {@link CallAttemptEntity}.
 */
@Entity
@Table(name = "call_quality")
public class CallQualityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "call_attempt_id", nullable = false)
    private CallAttemptEntity callAttempt;

    @Column(name = "score")
    private Integer score;

    @Column(name = "guardrail_violation")
    private Boolean guardrailViolation;

    @Column(name = "language_mismatch")
    private Boolean languageMismatch;

    @Column(name = "talked_over_client")
    private Boolean talkedOverClient;

    @Column(name = "outcome_correct")
    private Boolean outcomeCorrect;

    @Column(name = "goal_reached")
    private Boolean goalReached;

    @Column(name = "unanswered_question")
    private Boolean unansweredQuestion;

    @Column(name = "tool_misuse")
    private Boolean toolMisuse;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    @Column(name = "turn_count", nullable = false)
    private int turnCount;

    @Column(name = "repair_count", nullable = false)
    private int repairCount;

    @Column(name = "barge_in_count", nullable = false)
    private int bargeInCount;

    @Column(name = "no_input_prompt_count", nullable = false)
    private int noInputPromptCount;

    @Column(name = "low_confidence_count", nullable = false)
    private int lowConfidenceCount;

    @Column(name = "tool_error_count", nullable = false)
    private int toolErrorCount;

    @Column(name = "repeat_call", nullable = false)
    private boolean repeatCall;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", length = 20)
    private CallReviewStatus reviewStatus;

    @Column(name = "review_reason", columnDefinition = "TEXT")
    private String reviewReason;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "knowledge_item_id")
    private Long knowledgeItemId;

    @Column(name = "test_case_id")
    private Long testCaseId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public CallAttemptEntity getCallAttempt() { return callAttempt; }
    public void setCallAttempt(CallAttemptEntity callAttempt) { this.callAttempt = callAttempt; }

    public Long getCallAttemptId() { return callAttempt != null ? callAttempt.getId() : null; }
    public Long getAiAgentId() { return callAttempt != null ? callAttempt.getAiAgentId() : null; }
    public Integer getAgentVersion() { return callAttempt != null ? callAttempt.getAgentVersion() : null; }
    public Disposition getDisposition() { return callAttempt != null ? callAttempt.getDisposition() : null; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public Boolean getGuardrailViolation() { return guardrailViolation; }
    public void setGuardrailViolation(Boolean guardrailViolation) { this.guardrailViolation = guardrailViolation; }
    public Boolean getLanguageMismatch() { return languageMismatch; }
    public void setLanguageMismatch(Boolean languageMismatch) { this.languageMismatch = languageMismatch; }
    public Boolean getTalkedOverClient() { return talkedOverClient; }
    public void setTalkedOverClient(Boolean talkedOverClient) { this.talkedOverClient = talkedOverClient; }
    public Boolean getOutcomeCorrect() { return outcomeCorrect; }
    public void setOutcomeCorrect(Boolean outcomeCorrect) { this.outcomeCorrect = outcomeCorrect; }
    public Boolean getGoalReached() { return goalReached; }
    public void setGoalReached(Boolean goalReached) { this.goalReached = goalReached; }
    public Boolean getUnansweredQuestion() { return unansweredQuestion; }
    public void setUnansweredQuestion(Boolean unansweredQuestion) { this.unansweredQuestion = unansweredQuestion; }
    public Boolean getToolMisuse() { return toolMisuse; }
    public void setToolMisuse(Boolean toolMisuse) { this.toolMisuse = toolMisuse; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public boolean isNeedsReview() { return needsReview; }
    public void setNeedsReview(boolean needsReview) { this.needsReview = needsReview; }
    public int getTurnCount() { return turnCount; }
    public void setTurnCount(int turnCount) { this.turnCount = turnCount; }
    public int getRepairCount() { return repairCount; }
    public void setRepairCount(int repairCount) { this.repairCount = repairCount; }
    public int getBargeInCount() { return bargeInCount; }
    public void setBargeInCount(int bargeInCount) { this.bargeInCount = bargeInCount; }
    public int getNoInputPromptCount() { return noInputPromptCount; }
    public void setNoInputPromptCount(int noInputPromptCount) { this.noInputPromptCount = noInputPromptCount; }
    public int getLowConfidenceCount() { return lowConfidenceCount; }
    public void setLowConfidenceCount(int lowConfidenceCount) { this.lowConfidenceCount = lowConfidenceCount; }
    public int getToolErrorCount() { return toolErrorCount; }
    public void setToolErrorCount(int toolErrorCount) { this.toolErrorCount = toolErrorCount; }
    public boolean isRepeatCall() { return repeatCall; }
    public void setRepeatCall(boolean repeatCall) { this.repeatCall = repeatCall; }
    public CallReviewStatus getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(CallReviewStatus reviewStatus) { this.reviewStatus = reviewStatus; }
    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String reviewReason) { this.reviewReason = reviewReason; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public Long getKnowledgeItemId() { return knowledgeItemId; }
    public void setKnowledgeItemId(Long knowledgeItemId) { this.knowledgeItemId = knowledgeItemId; }
    public Long getTestCaseId() { return testCaseId; }
    public void setTestCaseId(Long testCaseId) { this.testCaseId = testCaseId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
