package uz.murodjon.uysotvoice.siptrunk.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.exception.ConflictException;
import uz.murodjon.uysotvoice.shared.exception.ErrorCode;
import uz.murodjon.uysotvoice.shared.exception.ExternalServiceException;
import uz.murodjon.uysotvoice.shared.exception.NotFoundException;
import uz.murodjon.uysotvoice.shared.exception.ValidationException;
import uz.murodjon.uysotvoice.shared.util.SecretCipher;
import uz.murodjon.uysotvoice.siptrunk.domain.SipTrunk;
import uz.murodjon.uysotvoice.siptrunk.dto.CreateSipTrunkRequest;
import uz.murodjon.uysotvoice.siptrunk.dto.SipTrunkFilter;
import uz.murodjon.uysotvoice.siptrunk.dto.SipTrunkRow;
import uz.murodjon.uysotvoice.siptrunk.dto.UpdateSipTrunkRequest;
import uz.murodjon.uysotvoice.siptrunk.enums.SipTrunkTransport;
import uz.murodjon.uysotvoice.siptrunk.repository.SipTrunkRepository;

import java.util.List;

/**
 * SIP trunk CRUD (ROADMAP B.3, report #7): each company may register several outbound
 * PJSIP trunks, exactly one of which is the default. Two modes, see {@link
 * SipTrunkRow#managed()} / {@code SipTrunkEntity}'s class javadoc: <strong>manual</strong>
 * (an admin already configured the endpoint by hand in {@code pjsip.conf}, this only
 * picks which one a call uses) or <strong>managed</strong> (real host/username/password
 * entered here — {@link PjsipConfigWriter} generates the PJSIP config and registers it
 * with Asterisk). {@link #findDefaultForCall} is the runtime hot path {@code AriService}
 * calls when originating a call — it must never throw, only the admin CRUD methods
 * below it do.
 */
@Service
public class SipTrunkService {

    private final SipTrunkRepository repo;
    private final PjsipConfigWriter pjsipConfig;
    private final SecretCipher cipher;
    private final CurrentCompany company;
    private final AuditService audit;

    public SipTrunkService(SipTrunkRepository repo, PjsipConfigWriter pjsipConfig, SecretCipher cipher,
                           CurrentCompany company, AuditService audit) {
        this.repo = repo;
        this.pjsipConfig = pjsipConfig;
        this.cipher = cipher;
        this.company = company;
        this.audit = audit;
    }

    @Transactional
    public SipTrunkRow create(CreateSipTrunkRequest r) {
        long id;
        if (isManaged(r.pjsipEndpoint(), r.host())) {
            requireUsername(r.sipUsername());
            requireValidTransport(r.transport());
            if (r.sipPassword() == null || r.sipPassword().isBlank()) {
                throw new ValidationException(ErrorCode.SIP_TRUNK_PASSWORD_REQUIRED);
            }
            id = repo.create(r.name(), pendingEndpoint(), r.callerId(), false,
                    r.host(), portOrDefault(r.port()), r.sipUsername(), encryptPassword(r.sipPassword()),
                    transportOrDefault(r.transport()));
            repo.updatePjsipEndpoint(id, generatedEndpoint(id));
        } else {
            id = repo.create(r.name(), r.pjsipEndpoint(), r.callerId(), false,
                    null, 5060, null, null, SipTrunkTransport.UDP);
        }
        pjsipConfig.regenerateAndReload();
        audit.record("SIP_TRUNK_CREATE", "sip_trunk", String.valueOf(id), r.name());
        return requireTrunk(id);
    }

    public SipTrunkRow update(long id, UpdateSipTrunkRequest r) {
        SipTrunk existing = requireSipTrunk(id);
        if (isManaged(r.pjsipEndpoint(), r.host())) {
            requireUsername(r.sipUsername());
            requireValidTransport(r.transport());
            String passwordEnc;
            if (r.sipPassword() != null && !r.sipPassword().isBlank()) {
                passwordEnc = encryptPassword(r.sipPassword());
            } else if (existing.host() != null) {
                passwordEnc = null; // repo.update keeps the trunk's current encrypted password
            } else {
                throw new ValidationException(ErrorCode.SIP_TRUNK_PASSWORD_REQUIRED_ON_SWITCH);
            }
            repo.update(id, r.name(), generatedEndpoint(id), r.callerId(), r.enabled(),
                    r.host(), portOrDefault(r.port()), r.sipUsername(), passwordEnc, transportOrDefault(r.transport()));
        } else {
            repo.update(id, r.name(), r.pjsipEndpoint(), r.callerId(), r.enabled(),
                    null, 5060, null, null, SipTrunkTransport.UDP);
        }
        pjsipConfig.regenerateAndReload();
        audit.record("SIP_TRUNK_UPDATE", "sip_trunk", String.valueOf(id), r.name());
        return requireTrunk(id);
    }

    /** Promotes {@code id} to the company's default trunk, demoting whichever one held it before. */
    public SipTrunkRow makeDefault(long id) {
        requireTrunk(id);
        repo.makeDefault(id);
        audit.record("SIP_TRUNK_SET_DEFAULT", "sip_trunk", String.valueOf(id), null);
        return requireTrunk(id);
    }

    /**
     * The default trunk cannot be deleted directly — promote another one first, so a
     * company is never left with calls silently falling back to the global config
     * without anyone having decided that on purpose.
     */
    public void delete(long id) {
        SipTrunk trunk = requireSipTrunk(id);
        if (trunk.isDefault()) {
            throw new ConflictException(ErrorCode.SIP_TRUNK_DEFAULT_DELETE_FORBIDDEN);
        }
        repo.delete(id);
        pjsipConfig.regenerateAndReload();
        audit.record("SIP_TRUNK_DELETE", "sip_trunk", String.valueOf(id), trunk.name());
    }

    public PageableData<SipTrunkRow> list(SipTrunkFilter filter) {
        List<SipTrunkRow> rows = repo.findAll(filter).stream().map(SipTrunkRow::of).toList();
        long total = repo.count(filter);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    /** As {@link SipTrunkRepository#find}, for the REST API — a missing trunk is a 404, not a null. */
    public SipTrunkRow requireTrunk(long id) {
        return SipTrunkRow.of(requireSipTrunk(id));
    }

    private SipTrunk requireSipTrunk(long id) {
        SipTrunk trunk = repo.find(id);
        if (trunk == null) {
            throw new NotFoundException(ErrorCode.SIP_TRUNK_NOT_FOUND, id);
        }
        return trunk;
    }

    /**
     * {@code companyId}'s enabled default trunk, or {@code null} if it has none —
     * called from {@code AriService} at call-origination time, on the call thread, so
     * this never throws; the caller falls back to the globally configured trunk.
     */
    public SipTrunkRow findDefaultForCall(long companyId) {
        SipTrunk trunk = repo.findDefaultForCompany(companyId);
        return trunk == null ? null : SipTrunkRow.of(trunk);
    }

    private static boolean isManaged(String pjsipEndpoint, String host) {
        boolean hasManual = pjsipEndpoint != null && !pjsipEndpoint.isBlank();
        boolean hasManaged = host != null && !host.isBlank();
        if (hasManual && hasManaged) {
            throw new ValidationException(ErrorCode.SIP_TRUNK_MODE_CONFLICT);
        }
        if (!hasManual && !hasManaged) {
            throw new ValidationException(ErrorCode.SIP_TRUNK_MODE_MISSING);
        }
        return hasManaged;
    }

    private static void requireUsername(String sipUsername) {
        if (sipUsername == null || sipUsername.isBlank()) {
            throw new ValidationException(ErrorCode.SIP_TRUNK_USERNAME_REQUIRED);
        }
    }

    private static void requireValidTransport(SipTrunkTransport transport) {
        if (transport != null && transport != SipTrunkTransport.UDP) {
            throw new ValidationException(ErrorCode.SIP_TRUNK_TRANSPORT_UNSUPPORTED, transport);
        }
    }

    private String encryptPassword(String plaintext) {
        if (!cipher.available()) {
            throw new ExternalServiceException(ErrorCode.ENCRYPTION_KEY_NOT_SET, "siptrunk");
        }
        return cipher.encrypt(plaintext);
    }

    private String generatedEndpoint(long id) {
        return "trunk_" + company.id() + "_" + id;
    }

    /** Overwritten by {@link #generatedEndpoint} once {@code id} exists — {@code pjsip_endpoint} is NOT NULL. */
    private static String pendingEndpoint() {
        return "pending";
    }

    private static int portOrDefault(Integer port) {
        return port != null && port > 0 ? port : 5060;
    }

    private static SipTrunkTransport transportOrDefault(SipTrunkTransport transport) {
        return transport != null ? transport : SipTrunkTransport.UDP;
    }
}
