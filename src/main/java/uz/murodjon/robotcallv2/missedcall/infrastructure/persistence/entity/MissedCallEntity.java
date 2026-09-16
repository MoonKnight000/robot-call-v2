package uz.murodjon.robotcallv2.missedcall.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallReason;
import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallStatus;

import java.time.Instant;

/** JPA entity for missed_call. */
@Entity
@Table(name = "missed_call")
public class MissedCallEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MissedCallReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MissedCallStatus status = MissedCallStatus.NEW;

    @Column(name = "call_count", nullable = false)
    private int callCount = 1;

    @Column(name = "first_at", nullable = false)
    private Instant firstAt;

    @Column(name = "last_at", nullable = false)
    private Instant lastAt;

    @Column(name = "queue_id")
    private Long queueId;

    @Column(name = "inbound_route_id")
    private Long inboundRouteId;

    @Column(name = "last_call_attempt_id")
    private Long lastCallAttemptId;

    @Column(name = "voicemail_message_id")
    private Long voicemailMessageId;

    @Column(name = "assigned_user_id")
    private Long assignedUserId;

    @Column(name = "callback_attempts", nullable = false)
    private int callbackAttempts;

    @Column(name = "last_callback_at")
    private Instant lastCallbackAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by_user_id")
    private Long resolvedByUserId;

    @Column(columnDefinition = "TEXT")
    private String note;

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public MissedCallReason getReason() {
        return reason;
    }

    public void setReason(MissedCallReason reason) {
        this.reason = reason;
    }

    public MissedCallStatus getStatus() {
        return status;
    }

    public void setStatus(MissedCallStatus status) {
        this.status = status;
    }

    public int getCallCount() {
        return callCount;
    }

    public void setCallCount(int callCount) {
        this.callCount = callCount;
    }

    public Instant getFirstAt() {
        return firstAt;
    }

    public void setFirstAt(Instant firstAt) {
        this.firstAt = firstAt;
    }

    public Instant getLastAt() {
        return lastAt;
    }

    public void setLastAt(Instant lastAt) {
        this.lastAt = lastAt;
    }

    public Long getQueueId() {
        return queueId;
    }

    public void setQueueId(Long queueId) {
        this.queueId = queueId;
    }

    public Long getInboundRouteId() {
        return inboundRouteId;
    }

    public void setInboundRouteId(Long inboundRouteId) {
        this.inboundRouteId = inboundRouteId;
    }

    public Long getLastCallAttemptId() {
        return lastCallAttemptId;
    }

    public void setLastCallAttemptId(Long lastCallAttemptId) {
        this.lastCallAttemptId = lastCallAttemptId;
    }

    public Long getVoicemailMessageId() {
        return voicemailMessageId;
    }

    public void setVoicemailMessageId(Long voicemailMessageId) {
        this.voicemailMessageId = voicemailMessageId;
    }

    public Long getAssignedUserId() {
        return assignedUserId;
    }

    public void setAssignedUserId(Long assignedUserId) {
        this.assignedUserId = assignedUserId;
    }

    public int getCallbackAttempts() {
        return callbackAttempts;
    }

    public void setCallbackAttempts(int callbackAttempts) {
        this.callbackAttempts = callbackAttempts;
    }

    public Instant getLastCallbackAt() {
        return lastCallbackAt;
    }

    public void setLastCallbackAt(Instant lastCallbackAt) {
        this.lastCallbackAt = lastCallbackAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Long getResolvedByUserId() {
        return resolvedByUserId;
    }

    public void setResolvedByUserId(Long resolvedByUserId) {
        this.resolvedByUserId = resolvedByUserId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
