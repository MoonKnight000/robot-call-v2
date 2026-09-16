package uz.murodjon.robotcallv2.voicemail.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.voicemail.domain.enums.VoicemailStatus;

import java.time.Instant;

/** JPA entity for voicemail_message. */
@Entity
@Table(name = "voicemail_message")
public class VoicemailMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "box_id", nullable = false)
    private Long boxId;

    @Column(name = "call_attempt_id")
    private Long callAttemptId;

    @Column(name = "caller_phone")
    private String callerPhone;

    @Column(name = "stored_file_id")
    private Long storedFileId;

    @Column(name = "duration_sec", nullable = false)
    private int durationSec;

    @Column(columnDefinition = "TEXT")
    private String transcript;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VoicemailStatus status = VoicemailStatus.NEW;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public Long getBoxId() {
        return boxId;
    }

    public void setBoxId(Long boxId) {
        this.boxId = boxId;
    }

    public Long getCallAttemptId() {
        return callAttemptId;
    }

    public void setCallAttemptId(Long callAttemptId) {
        this.callAttemptId = callAttemptId;
    }

    public String getCallerPhone() {
        return callerPhone;
    }

    public void setCallerPhone(String callerPhone) {
        this.callerPhone = callerPhone;
    }

    public Long getStoredFileId() {
        return storedFileId;
    }

    public void setStoredFileId(Long storedFileId) {
        this.storedFileId = storedFileId;
    }

    public int getDurationSec() {
        return durationSec;
    }

    public void setDurationSec(int durationSec) {
        this.durationSec = durationSec;
    }

    public String getTranscript() {
        return transcript;
    }

    public void setTranscript(String transcript) {
        this.transcript = transcript;
    }

    public VoicemailStatus getStatus() {
        return status;
    }

    public void setStatus(VoicemailStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
