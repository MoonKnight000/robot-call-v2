package uz.murodjon.uysotvoice.callrecord.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import uz.murodjon.uysotvoice.shared.dialog.ReasonCode;
import uz.murodjon.uysotvoice.shared.dialog.ReasonCodeConverter;
import uz.murodjon.uysotvoice.shared.dialog.Sentiment;
import uz.murodjon.uysotvoice.shared.dialog.SentimentConverter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** JPA entity for {@code call_result} (§4.3), the single post-call summary row. */
@Entity
@Table(name = "call_result")
public class CallResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "call_id", nullable = false, unique = true)
    private CallAttemptEntity call;

    @Column(nullable = false)
    private String summary;

    @Convert(converter = ReasonCodeConverter.class)
    @Column(name = "reason_code")
    private ReasonCode reasonCode;

    @Column(name = "promised_date")
    private LocalDate promisedDate;

    @Column(name = "promised_amount")
    private BigDecimal promisedAmount;

    @Convert(converter = SentimentConverter.class)
    @Column
    private Sentiment sentiment;

    @Column(name = "needs_follow_up", nullable = false)
    private boolean needsFollowUp;

    @Column(name = "follow_up_note")
    private String followUpNote;

    @Column(nullable = false)
    private boolean escalated;

    @Column(name = "crm_note_id")
    private Long crmNoteId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "crm_attempts", nullable = false)
    private int crmAttempts;

    @Column(name = "crm_last_error")
    private String crmLastError;

    /**
     * The full scenario-shaped outcome as JSON (ROADMAP A.3) — {@code reasonCode}/
     * {@code promisedDate}/{@code promisedAmount} for {@code debt-collection},
     * {@code answers}/{@code score} for {@code survey}, etc. The fixed columns above
     * are a dual-write for whichever scenario happens to use those exact field names.
     */
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String outcome;

    public Long getId() {
        return id;
    }

    public CallAttemptEntity getCall() {
        return call;
    }

    public void setCall(CallAttemptEntity call) {
        this.call = call;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public ReasonCode getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(ReasonCode reasonCode) {
        this.reasonCode = reasonCode;
    }

    public LocalDate getPromisedDate() {
        return promisedDate;
    }

    public void setPromisedDate(LocalDate promisedDate) {
        this.promisedDate = promisedDate;
    }

    public BigDecimal getPromisedAmount() {
        return promisedAmount;
    }

    public void setPromisedAmount(BigDecimal promisedAmount) {
        this.promisedAmount = promisedAmount;
    }

    public Sentiment getSentiment() {
        return sentiment;
    }

    public void setSentiment(Sentiment sentiment) {
        this.sentiment = sentiment;
    }

    public boolean isNeedsFollowUp() {
        return needsFollowUp;
    }

    public void setNeedsFollowUp(boolean needsFollowUp) {
        this.needsFollowUp = needsFollowUp;
    }

    public String getFollowUpNote() {
        return followUpNote;
    }

    public void setFollowUpNote(String followUpNote) {
        this.followUpNote = followUpNote;
    }

    public boolean isEscalated() {
        return escalated;
    }

    public void setEscalated(boolean escalated) {
        this.escalated = escalated;
    }

    public Long getCrmNoteId() {
        return crmNoteId;
    }

    public void setCrmNoteId(Long crmNoteId) {
        this.crmNoteId = crmNoteId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public int getCrmAttempts() {
        return crmAttempts;
    }

    public void setCrmAttempts(int crmAttempts) {
        this.crmAttempts = crmAttempts;
    }

    public String getCrmLastError() {
        return crmLastError;
    }

    public void setCrmLastError(String crmLastError) {
        this.crmLastError = crmLastError;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }
}
