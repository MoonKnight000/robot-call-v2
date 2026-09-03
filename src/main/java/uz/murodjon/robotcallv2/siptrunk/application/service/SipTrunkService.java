package uz.murodjon.robotcallv2.siptrunk.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.agent.ami.AmiClient;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.*;
import uz.murodjon.robotcallv2.shared.util.SecretCipher;
import uz.murodjon.robotcallv2.siptrunk.application.dto.*;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.SipTrunkUseCase;
import uz.murodjon.robotcallv2.siptrunk.application.port.output.SipTrunkRepository;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunk;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.SipTrunkTransport;
import uz.murodjon.robotcallv2.siptrunk.domain.service.SipTrunkValidator;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * SIP trunk CRUD and live status monitoring (ROADMAP B.3, report #7).
 */
@Service
public class SipTrunkService implements SipTrunkUseCase {

    private static final Logger log = LoggerFactory.getLogger(SipTrunkService.class);

    private final SipTrunkRepository repo;
    private final PjsipConfigWriter pjsipConfig;
    private final SecretCipher cipher;
    private final CurrentCompany company;
    private final AuditService audit;
    private final AmiClient ami;

    public SipTrunkService(SipTrunkRepository repo, PjsipConfigWriter pjsipConfig, SecretCipher cipher,
                           CurrentCompany company, AuditService audit, AmiClient ami) {
        this.repo = repo;
        this.pjsipConfig = pjsipConfig;
        this.cipher = cipher;
        this.company = company;
        this.audit = audit;
        this.ami = ami;
    }

    @Override
    @Transactional
    public SipTrunkRow create(CreateSipTrunkRequest r) {
        long id;
        if (SipTrunkValidator.isManaged(r.pjsipEndpoint(), r.host())) {
            SipTrunkValidator.requireUsername(r.sipUsername());
            SipTrunkValidator.requireValidTransport(r.transport());
            if (r.sipPassword() == null || r.sipPassword().isBlank()) {
                throw new ValidationException(ErrorCode.SIP_TRUNK_PASSWORD_REQUIRED);
            }
            id = repo.create(r.name(), pendingEndpoint(), r.callerId(), false,
                    r.host(), SipTrunkValidator.portOrDefault(r.port()), r.sipUsername(), encryptPassword(r.sipPassword()),
                    SipTrunkValidator.transportOrDefault(r.transport()), r.codecs());
            repo.updatePjsipEndpoint(id, generatedEndpoint(id));
        } else {
            id = repo.create(r.name(), r.pjsipEndpoint(), r.callerId(), false,
                    null, 5060, null, null, SipTrunkTransport.UDP, r.codecs());
        }
        pjsipConfig.regenerateAndReload();
        audit.record("SIP_TRUNK_CREATE", "sip_trunk", String.valueOf(id), r.name());
        return requireTrunk(id);
    }

    @Override
    public SipTrunkRow update(long id, UpdateSipTrunkRequest r) {
        SipTrunk existing = requireSipTrunk(id);
        if (SipTrunkValidator.isManaged(r.pjsipEndpoint(), r.host())) {
            SipTrunkValidator.requireUsername(r.sipUsername());
            SipTrunkValidator.requireValidTransport(r.transport());
            String passwordEnc;
            if (r.sipPassword() != null && !r.sipPassword().isBlank()) {
                passwordEnc = encryptPassword(r.sipPassword());
            } else if (existing.host() != null) {
                passwordEnc = null; // repo.update keeps the trunk's current encrypted password
            } else {
                throw new ValidationException(ErrorCode.SIP_TRUNK_PASSWORD_REQUIRED_ON_SWITCH);
            }
            repo.update(id, r.name(), generatedEndpoint(id), r.callerId(), r.enabled(),
                    r.host(), SipTrunkValidator.portOrDefault(r.port()), r.sipUsername(), passwordEnc,
                    SipTrunkValidator.transportOrDefault(r.transport()), r.codecs());
        } else {
            repo.update(id, r.name(), r.pjsipEndpoint(), r.callerId(), r.enabled(),
                    null, 5060, null, null, SipTrunkTransport.UDP, r.codecs());
        }
        pjsipConfig.regenerateAndReload();
        audit.record("SIP_TRUNK_UPDATE", "sip_trunk", String.valueOf(id), r.name());
        return requireTrunk(id);
    }

    @Override
    public SipTrunkRow makeDefault(long id) {
        requireTrunk(id);
        repo.makeDefault(id);
        audit.record("SIP_TRUNK_SET_DEFAULT", "sip_trunk", String.valueOf(id), null);
        return requireTrunk(id);
    }

    @Override
    public void delete(long id) {
        SipTrunk trunk = requireSipTrunk(id);
        if (trunk.isDefault()) {
            throw new ConflictException(ErrorCode.SIP_TRUNK_DEFAULT_DELETE_FORBIDDEN);
        }
        repo.delete(id);
        pjsipConfig.regenerateAndReload();
        audit.record("SIP_TRUNK_DELETE", "sip_trunk", String.valueOf(id), trunk.name());
    }

    @Override
    public PageableData<SipTrunkRow> list(SipTrunkFilter filter) {
        List<SipTrunkRow> rows = repo.findAll(filter).stream().map(SipTrunkRow::of).toList();
        long total = repo.count(filter);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    @Override
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

    @Override
    public SipTrunkRow findDefaultForCall(long companyId) {
        SipTrunk trunk = repo.findDefaultForCompany(companyId);
        return trunk == null ? null : SipTrunkRow.of(trunk);
    }

    /**
     * The trunks a caller may dial through: the ones it picked, or every enabled trunk of
     * the company when it picked none.
     *
     * <p>A picked set that resolves to nothing — every one of them disabled, deleted, or
     * belonging to another company — is an error, not a reason to fall back. Falling back
     * quietly sent a campaign's calls out over trunks its owner had deliberately excluded,
     * which is the one outcome picking trunks exists to prevent. Some of the picked set
     * still missing is only logged: the rest were chosen too, and they can carry the call.
     */
    @Override
    public List<SipTrunkRow> findTrunksForCall(long companyId, Collection<Long> candidateTrunkIds) {
        if (candidateTrunkIds == null || candidateTrunkIds.isEmpty()) {
            return findAllEnabledForCompany(companyId);
        }
        List<SipTrunk> matched = repo.findEnabledByIdsAndCompany(candidateTrunkIds, companyId);
        if (matched.isEmpty()) {
            throw new ConflictException(ErrorCode.SIP_TRUNK_SELECTION_UNAVAILABLE, candidateTrunkIds);
        }
        if (matched.size() < candidateTrunkIds.size()) {
            log.warn("Company {}: {} of the {} selected SIP trunks are unavailable, dialling over the rest",
                    companyId, candidateTrunkIds.size() - matched.size(), candidateTrunkIds.size());
        }
        return matched.stream().map(SipTrunkRow::of).toList();
    }

    @Override
    public List<SipTrunkRow> findAllEnabledForCompany(long companyId) {
        List<SipTrunk> enabled = repo.findAllEnabledByCompany(companyId);
        if (enabled.isEmpty()) {
            SipTrunk def = repo.findDefaultForCompany(companyId);
            if (def != null) {
                return List.of(SipTrunkRow.of(def));
            }
            return List.of();
        }
        return enabled.stream().map(SipTrunkRow::of).toList();
    }

    @Override
    public SipTrunkStatus getStatus(long id) {
        SipTrunk trunk = requireSipTrunk(id);
        return evaluateTrunkStatus(trunk);
    }

    @Override
    public List<SipTrunkStatus> getAllStatuses() {
        List<SipTrunk> trunks = repo.findAll(new SipTrunkFilter(0, 100, null));
        return trunks.stream().map(this::evaluateTrunkStatus).toList();
    }

    private SipTrunkStatus evaluateTrunkStatus(SipTrunk trunk) {
        if (!trunk.enabled()) {
            return new SipTrunkStatus(trunk.id(), trunk.name(), trunk.pjsipEndpoint(),
                    trunk.host() != null, false, trunk.isDefault(),
                    "DISABLED", false, "Trunk o'chirilgan (Disabled)", Instant.now());
        }

        String endpoint = trunk.pjsipEndpoint();
        boolean managed = trunk.host() != null;

        if (managed) {
            String regCmd = "pjsip show registration " + endpoint + "-reg";
            String regOutput = ami.executeCommand(regCmd);

            if (regOutput != null && !regOutput.isBlank()) {
                String lower = regOutput.toLowerCase();
                if (lower.contains("registered")) {
                    return new SipTrunkStatus(trunk.id(), trunk.name(), endpoint, true, true, trunk.isDefault(),
                            "REGISTERED", true, "Muvaffaqiyatli ro'yxatdan o'tgan (" + trunk.host() + ":" + trunk.port() + ")", Instant.now());
                } else if (lower.contains("rejected") || lower.contains("auth_failed")) {
                    return new SipTrunkStatus(trunk.id(), trunk.name(), endpoint, true, true, trunk.isDefault(),
                            "REJECTED", false, "Ro'yxatdan o'tish rad etildi (login yoki parol xato)", Instant.now());
                } else if (lower.contains("unregistered")) {
                    return new SipTrunkStatus(trunk.id(), trunk.name(), endpoint, true, true, trunk.isDefault(),
                            "UNREGISTERED", false, "Ro'yxatdan o'tmagan (Unregistered)", Instant.now());
                } else if (lower.contains("trying")) {
                    return new SipTrunkStatus(trunk.id(), trunk.name(), endpoint, true, true, trunk.isDefault(),
                            "TRYING", false, "Provayderga ulanish kutilmoqda...", Instant.now());
                }
            }
        }

        String epOutput = ami.executeCommand("pjsip show endpoint " + endpoint);
        if (epOutput != null && !epOutput.isBlank()) {
            String lower = epOutput.toLowerCase();
            if (lower.contains("avail") || lower.contains("not in use") || lower.contains("in use") || lower.contains("ring")) {
                return new SipTrunkStatus(trunk.id(), trunk.name(), endpoint, managed, true, trunk.isDefault(),
                        "ONLINE", true, "Endpoint faol va ulanish mavjud", Instant.now());
            } else if (lower.contains("unavail")) {
                return new SipTrunkStatus(trunk.id(), trunk.name(), endpoint, managed, true, trunk.isDefault(),
                        "UNAVAILABLE", false, "Endpoint ulanmagan / oflayn", Instant.now());
            } else if (lower.contains("doesn't exist") || lower.contains("unable to find") || lower.contains("not found")) {
                return new SipTrunkStatus(trunk.id(), trunk.name(), endpoint, managed, true, trunk.isDefault(),
                        "NOT_FOUND", false, "Asteriskda endpoint topilmadi", Instant.now());
            }
        }

        return new SipTrunkStatus(trunk.id(), trunk.name(), endpoint, managed, true, trunk.isDefault(),
                "ACTIVE", true, "Trunk faol holatda", Instant.now());
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

    private static String pendingEndpoint() {
        return "pending";
    }
}
