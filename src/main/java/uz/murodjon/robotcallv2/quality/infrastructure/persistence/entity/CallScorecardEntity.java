package uz.murodjon.robotcallv2.quality.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.quality.domain.enums.ScorecardReviewStatus;
import uz.murodjon.robotcallv2.shared.dialog.Sentiment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** JPA entity for call_scorecard, with its answers. */
@Entity
@Table(name = "call_scorecard")
public class CallScorecardEntity {

    @Id
    @Column(name = "call_id")
    private Long callId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "operator_user_id")
    private Long operatorUserId;

    @Column
    private Integer score;

    @Column(name = "judged_score")
    private Integer judgedScore;

    @Enumerated(EnumType.STRING)
    @Column
    private Sentiment sentiment;

    @ElementCollection
    @CollectionTable(name = "call_scorecard_topic", joinColumns = @JoinColumn(name = "call_id"))
    @Column(name = "topic")
    private Set<String> topics = new LinkedHashSet<>();

    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status")
    private ScorecardReviewStatus reviewStatus;

    @Column(name = "review_note")
    private String reviewNote;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "prompt_tokens", nullable = false)
    private long promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private long completionTokens;

    @Column(name = "cached_tokens", nullable = false)
    private long cachedTokens;

    @OneToMany(mappedBy = "scorecard", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<CallScorecardAnswerEntity> answers = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getCallId() {
        return callId;
    }

    public void setCallId(Long callId) {
        this.callId = callId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Integer getJudgedScore() {
        return judgedScore;
    }

    public void setJudgedScore(Integer judgedScore) {
        this.judgedScore = judgedScore;
    }

    public Sentiment getSentiment() {
        return sentiment;
    }

    public void setSentiment(Sentiment sentiment) {
        this.sentiment = sentiment;
    }

    public Set<String> getTopics() {
        return topics;
    }

    public boolean isNeedsReview() {
        return needsReview;
    }

    public void setNeedsReview(boolean needsReview) {
        this.needsReview = needsReview;
    }

    public ScorecardReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(ScorecardReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(Long reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public long getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(long promptTokens) {
        this.promptTokens = promptTokens;
    }

    public long getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(long completionTokens) {
        this.completionTokens = completionTokens;
    }

    public long getCachedTokens() {
        return cachedTokens;
    }

    public void setCachedTokens(long cachedTokens) {
        this.cachedTokens = cachedTokens;
    }

    public List<CallScorecardAnswerEntity> getAnswers() {
        return answers;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
