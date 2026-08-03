package uz.murodjon.uysotvoice.callrecord.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import uz.murodjon.uysotvoice.campaign.entity.CampaignTargetEntity;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;

import java.time.Instant;

/** JPA entity for {@code call_attempt} (PROJECT.md §6, Stage 9). */
@Entity
@Table(name = "call_attempt")
public class CallAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id", nullable = false)
    private CampaignTargetEntity target;

    @Column(name = "sip_call_id")
    private String sipCallId;

    @Column(name = "asterisk_channel")
    private String asteriskChannel;

    @Column(nullable = false)
    private String language;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Enumerated(EnumType.STRING)
    @Column
    private Disposition disposition;

    @Column(name = "hangup_cause")
    private String hangupCause;

    @Column(name = "recording_url")
    private String recordingUrl;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "finalize_attempts", nullable = false)
    private int finalizeAttempts;

    @Column(name = "company_id", nullable = false)
    private long companyId;

    /** Which inbound_route this call matched (ROADMAP C.1); null for outbound/manual calls. */
    @Column(name = "inbound_route_id")
    private Long inboundRouteId;

    /**
     * Which panel user handled this call (§15 "Bugun" mini-statistika) — set only when a
     * call was transferred to a human and the answering endpoint matched a user's {@code
     * app_user.sip_extension}. Null for every bot-only call.
     */
    @Column(name = "operator_user_id")
    private Long operatorUserId;

    public Long getId() {
        return id;
    }

    public CampaignTargetEntity getTarget() {
        return target;
    }

    public void setTarget(CampaignTargetEntity target) {
        this.target = target;
    }

    public String getSipCallId() {
        return sipCallId;
    }

    public void setSipCallId(String sipCallId) {
        this.sipCallId = sipCallId;
    }

    public String getAsteriskChannel() {
        return asteriskChannel;
    }

    public void setAsteriskChannel(String asteriskChannel) {
        this.asteriskChannel = asteriskChannel;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }

    public void setAnsweredAt(Instant answeredAt) {
        this.answeredAt = answeredAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public Integer getDurationSec() {
        return durationSec;
    }

    public void setDurationSec(Integer durationSec) {
        this.durationSec = durationSec;
    }

    public Disposition getDisposition() {
        return disposition;
    }

    public void setDisposition(Disposition disposition) {
        this.disposition = disposition;
    }

    public String getHangupCause() {
        return hangupCause;
    }

    public void setHangupCause(String hangupCause) {
        this.hangupCause = hangupCause;
    }

    public String getRecordingUrl() {
        return recordingUrl;
    }

    public void setRecordingUrl(String recordingUrl) {
        this.recordingUrl = recordingUrl;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public int getFinalizeAttempts() {
        return finalizeAttempts;
    }

    public void setFinalizeAttempts(int finalizeAttempts) {
        this.finalizeAttempts = finalizeAttempts;
    }

    public long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(long companyId) {
        this.companyId = companyId;
    }

    public Long getInboundRouteId() {
        return inboundRouteId;
    }

    public void setInboundRouteId(Long inboundRouteId) {
        this.inboundRouteId = inboundRouteId;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }
}
