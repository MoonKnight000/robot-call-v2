package uz.murodjon.robotcallv2.siptrunk.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.shared.converter.StringListConverter;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.SipTrunkTransport;

import java.time.Instant;
import java.util.List;

/**
 * JPA entity for sip_trunk (ROADMAP B.3).
 */
@Entity
@Table(name = "sip_trunk")
public class SipTrunkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private long companyId;

    @Column(nullable = false)
    private String name;

    @Column(name = "pjsip_endpoint", nullable = false)
    private String pjsipEndpoint;

    @Column(name = "caller_id")
    private String callerId;

    @Column
    private String host;

    @Column(nullable = false)
    private int port = 5060;

    @Column(name = "sip_username")
    private String sipUsername;

    @Column(name = "sip_password_enc")
    private String sipPasswordEnc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SipTrunkTransport transport = SipTrunkTransport.UDP;

    @Convert(converter = StringListConverter.class)
    @Column(name = "codecs")
    private List<String> codecs;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(long companyId) {
        this.companyId = companyId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPjsipEndpoint() {
        return pjsipEndpoint;
    }

    public void setPjsipEndpoint(String pjsipEndpoint) {
        this.pjsipEndpoint = pjsipEndpoint;
    }

    public String getCallerId() {
        return callerId;
    }

    public void setCallerId(String callerId) {
        this.callerId = callerId;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getSipUsername() {
        return sipUsername;
    }

    public void setSipUsername(String sipUsername) {
        this.sipUsername = sipUsername;
    }

    public String getSipPasswordEnc() {
        return sipPasswordEnc;
    }

    public void setSipPasswordEnc(String sipPasswordEnc) {
        this.sipPasswordEnc = sipPasswordEnc;
    }

    public SipTrunkTransport getTransport() {
        return transport;
    }

    public void setTransport(SipTrunkTransport transport) {
        this.transport = transport;
    }

    public List<String> getCodecs() {
        return codecs;
    }

    public void setCodecs(List<String> codecs) {
        this.codecs = codecs;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
