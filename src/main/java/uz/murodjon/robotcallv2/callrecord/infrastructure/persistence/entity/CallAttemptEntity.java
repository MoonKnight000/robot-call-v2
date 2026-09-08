package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignTargetEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignVariantEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.inbound.infrastructure.persistence.entity.InboundRouteEntity;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.storage.infrastructure.persistence.entity.StoredFileEntity;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.entity.UserEntity;

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

    /** The dialled number; null on rows written before V4. */
    @Column
    private String phone;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recording_file_id")
    private StoredFileEntity recordingFile;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "finalize_attempts", nullable = false)
    private int finalizeAttempts;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    /** Which inbound_route this call matched (ROADMAP C.1); null for outbound/manual calls. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inbound_route_id")
    private InboundRouteEntity inboundRoute;

    /**
     * Which panel user handled this call (§15 "Bugun" mini-statistika) — set only when a
     * call was transferred to a human and the answering endpoint matched a user's {@code
     * app_user.sip_extension}. Null for every bot-only call.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_user_id")
    private UserEntity operatorUser;

    /**
     * The A/B variant this call ran, or null when its campaign is not testing.
     *
     * <p>A plain id rather than a relation: nothing here ever needs the variant's row, and
     * the column exists so that a conversion reported days later can still be credited to
     * the script that earned it.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private CampaignVariantEntity variant;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
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

    public StoredFileEntity getRecordingFile() {
        return recordingFile;
    }

    public void setRecordingFile(StoredFileEntity recordingFile) {
        this.recordingFile = recordingFile;
    }

    public Long getRecordingFileId() {
        return recordingFile != null ? recordingFile.getId() : null;
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

    public CompanyEntity getCompany() {
        return company;
    }

    public void setCompany(CompanyEntity company) {
        this.company = company;
    }

    public long getCompanyId() {
        return company != null ? company.getId() : 0L;
    }

    public InboundRouteEntity getInboundRoute() {
        return inboundRoute;
    }

    public void setInboundRoute(InboundRouteEntity inboundRoute) {
        this.inboundRoute = inboundRoute;
    }

    public Long getInboundRouteId() {
        return inboundRoute != null ? inboundRoute.getId() : null;
    }

    public UserEntity getOperatorUser() {
        return operatorUser;
    }

    public void setOperatorUser(UserEntity operatorUser) {
        this.operatorUser = operatorUser;
    }

    public CampaignVariantEntity getVariant() {
        return variant;
    }

    public void setVariant(CampaignVariantEntity variant) {
        this.variant = variant;
    }

    public Long getVariantId() {
        return variant != null ? variant.getId() : null;
    }

    public Long getOperatorUserId() {
        return operatorUser != null ? operatorUser.getId() : null;
    }
}
