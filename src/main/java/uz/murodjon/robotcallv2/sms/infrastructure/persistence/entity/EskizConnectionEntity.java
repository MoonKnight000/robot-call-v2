package uz.murodjon.robotcallv2.sms.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** JPA entity for eskiz_connection: a company's own Eskiz login, encrypted. */
@Entity
@Table(name = "eskiz_connection")
public class EskizConnectionEntity {

    @Id
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "email_enc", nullable = false)
    private String emailEnc;

    @Column(name = "password_enc", nullable = false)
    private String passwordEnc;

    private String sender;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public String getEmailEnc() {
        return emailEnc;
    }

    public void setEmailEnc(String emailEnc) {
        this.emailEnc = emailEnc;
    }

    public String getPasswordEnc() {
        return passwordEnc;
    }

    public void setPasswordEnc(String passwordEnc) {
        this.passwordEnc = passwordEnc;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(Instant connectedAt) {
        this.connectedAt = connectedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
