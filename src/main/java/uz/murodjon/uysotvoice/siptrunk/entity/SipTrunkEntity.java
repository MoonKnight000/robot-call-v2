package uz.murodjon.uysotvoice.siptrunk.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import uz.murodjon.uysotvoice.siptrunk.enums.SipTrunkTransport;

import java.time.Instant;

/**
 * JPA entity for {@code sip_trunk} (ROADMAP B.3) — one of a company's outbound PJSIP
 * trunks, in one of two modes (report #7):
 *
 * <ul>
 *   <li><strong>Managed</strong> — {@code host}/{@code sipUsername}/{@code
 *   sipPasswordEnc} are set, {@code pjsipEndpoint} is app-generated ({@code
 *   trunk_<companyId>_<id>}). {@code siptrunk.service.PjsipConfigWriter} renders the
 *   matching PJSIP sections under that name into a file Asterisk {@code #include}s, and
 *   {@code agent.ami.AmiClient} reloads {@code res_pjsip.so} — the app actually
 *   registers this trunk with the provider.
 *   <li><strong>Manual</strong> (the original ROADMAP B.3 shape, e.g. {@code
 *   SipTrunkBootstrap}'s migration row) — {@code host}/{@code sipUsername}/{@code
 *   sipPasswordEnc} are {@code null}, {@code pjsipEndpoint} names an endpoint an
 *   operator already configured by hand in {@code pjsip.conf}. No config is generated,
 *   no reload happens; this project only picks which endpoint a call uses.
 * </ul>
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

    /** Managed mode only — SIP provider's host/domain ({@code server_uri}/{@code client_uri}/{@code from_domain}). */
    @Column
    private String host;

    @Column(nullable = false)
    private int port = 5060;

    @Column(name = "sip_username")
    private String sipUsername;

    /** {@code shared.util.SecretCipher} ciphertext, never plaintext — mirrors {@code CrmIntegrationEntity}. */
    @Column(name = "sip_password_enc")
    private String sipPasswordEnc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SipTrunkTransport transport = SipTrunkTransport.UDP;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
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
