package uz.murodjon.uysotvoice.callrecord.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** JPA entity for {@code call_result} (§4.3), the single post-call summary row. */
@Entity
@Table(name = "call_result")
public class CallResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "call_id", nullable = false, unique = true)
    private long callId;

    @Column(nullable = false)
    private String summary;

    @Column(name = "reason_code")
    private String reasonCode;

    @Column(name = "promised_date")
    private LocalDate promisedDate;

    @Column(name = "promised_amount")
    private BigDecimal promisedAmount;

    @Column
    private String sentiment;

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

    public Long getId() {
        return id;
    }

    public long getCallId() {
        return callId;
    }

    public void setCallId(long callId) {
        this.callId = callId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
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

    public String getSentiment() {
        return sentiment;
    }

    public void setSentiment(String sentiment) {
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
}
