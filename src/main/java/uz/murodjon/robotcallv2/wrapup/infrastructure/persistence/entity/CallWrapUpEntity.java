package uz.murodjon.robotcallv2.wrapup.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.wrapup.domain.enums.WrapUpOutcome;

import java.time.Instant;

/** JPA entity for call_wrap_up — at most one per call, keyed by the call. */
@Entity
@Table(name = "call_wrap_up")
public class CallWrapUpEntity {

    @Id
    @Column(name = "call_attempt_id")
    private Long callAttemptId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "wrap_up_code_id")
    private Long wrapUpCodeId;

    @Column(name = "code_name", nullable = false)
    private String codeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WrapUpOutcome outcome;

    @Column(name = "operator_user_id", nullable = false)
    private Long operatorUserId;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "callback_at")
    private Instant callbackAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getCallAttemptId() {
        return callAttemptId;
    }

    public void setCallAttemptId(Long callAttemptId) {
        this.callAttemptId = callAttemptId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public Long getWrapUpCodeId() {
        return wrapUpCodeId;
    }

    public void setWrapUpCodeId(Long wrapUpCodeId) {
        this.wrapUpCodeId = wrapUpCodeId;
    }

    public String getCodeName() {
        return codeName;
    }

    public void setCodeName(String codeName) {
        this.codeName = codeName;
    }

    public WrapUpOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(WrapUpOutcome outcome) {
        this.outcome = outcome;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getCallbackAt() {
        return callbackAt;
    }

    public void setCallbackAt(Instant callbackAt) {
        this.callbackAt = callbackAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
