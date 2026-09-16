package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallEventType;

import java.time.Instant;

/** JPA entity for call_event. Append-only. */
@Entity
@Table(name = "call_event")
public class CallEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "call_attempt_id", nullable = false)
    private Long callAttemptId;

    @Column(nullable = false)
    private Instant at;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallEventType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public Long getCallAttemptId() {
        return callAttemptId;
    }

    public void setCallAttemptId(Long callAttemptId) {
        this.callAttemptId = callAttemptId;
    }

    public Instant getAt() {
        return at;
    }

    public void setAt(Instant at) {
        this.at = at;
    }

    public CallEventType getType() {
        return type;
    }

    public void setType(CallEventType type) {
        this.type = type;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
