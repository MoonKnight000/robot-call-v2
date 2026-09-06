package uz.murodjon.robotcallv2.siptrunk.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.agent.ami.AmiClient;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.*;
import uz.murodjon.robotcallv2.shared.util.SecretCipher;
import uz.murodjon.robotcallv2.siptrunk.application.dto.*;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.SipTrunkUseCase;
import uz.murodjon.robotcallv2.siptrunk.application.port.output.SipTrunkRepository;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunk;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunkFilter;
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

    private final SipTrunkRepository repository;
    private final PjsipConfigWriter pjsipConfig;
    private final SecretCipher cipher;
    private final AuditService audit;
    private final AmiClient ami;

    public SipTrunkService(SipTrunkRepository repository, PjsipConfigWriter pjsipConfig, SecretCipher cipher,
                           AuditService audit, AmiClient ami) {
        this.repository = repository;
        this.pjsipConfig = pjsipConfig;
        this.cipher = cipher;
        this.audit = audit;
        this.ami = ami;
    }

    @Override
    @Transactional
    public SipTrunkRow create(long companyId, CreateSipTrunkRequest r) {
        long id;
        if (SipTrunkValidator.isManaged(r.pjsipEndpoint(), r.host())) {
            SipTrunkValidator.requireUsername(r.sipUsername());
            SipTrunkValidator.requireValidTransport(r.transport());
            if (r.sipPassword() == null || r.sipPassword().isBlank()) {
                throw new ValidationException(ErrorCode.SIP_TRUNK_PASSWORD_REQUIRED);
            }
            id = repository.create(companyId, r.name(), pendingEndpoint(), r.callerId(), false,
                    r.host(), SipTrunkValidator.portOrDefault(r.port()), r.sipUsername(), encryptPassword(r.sipPassword()),
                    SipTrunkValidator.transportOrDefault(r.transport()), r.codecs());
            repository.updatePjsipEndpoint(companyId, id, generatedEndpoint(companyId, id));
        } else {
            id = repository.create(companyId, r.name(), r.pjsipEndpoint(), r.callerId(), false,
                    null, 5060, null, null, SipTrunkTransport.UDP, r.codecs());
        }
        pjsipConfig.regenerateAndReload();
        audit.record(companyId, "SIP_TRUNK_CREATE", "sip_trunk", String.valueOf(id), r.name());
        return requireTrunk(companyId, id);
    }

    @Override
    public SipTrunkRow update(long companyId, long id, UpdateSipTrunkRequest r) {
        SipTrunk existing = requireSipTrunk(companyId, id);
        if (SipTrunkValidator.isManaged(r.pjsipEndpoint(), r.host())) {
            SipTrunkValidator.requireUsername(r.sipUsername());
            SipTrunkValidator.requireValidTransport(r.transport());
            String passwordEnc;
            if (r.sipPassword() != null && !r.sipPassword().isBlank()) {
                passwordEnc = encryptPassword(r.sipPassword());
            } else if (existing.host() != null) {
                passwordEnc = null; // repository.update keeps the trunk's current encrypted password
            } else {
                throw new ValidationException(ErrorCode.SIP_TRUNK_PASSWORD_REQUIRED_ON_SWITCH);
            }
            repository.update(companyId, id, r.name(), generatedEndpoint(companyId, id), r.callerId(), r.enabled(),
                    r.host(), SipTrunkValidator.portOrDefault(r.port()), r.sipUsername(), passwordEnc,
                    SipTrunkValidator.transportOrDefault(r.transport()), r.codecs());
        } else {
            repository.update(companyId, id, r.name(), r.pjsipEndpoint(), r.callerId(), r.enabled(),
                    null, 5060, null, null, SipTrunkTransport.UDP, r.codecs());
        }
        pjsipConfig.regenerateAndReload();
        audit.record(companyId, "SIP_TRUNK_UPDATE", "sip_trunk", String.valueOf(id), r.name());
        return requireTrunk(companyId, id);
    }

    @Override
    public SipTrunkRow makeDefault(long companyId, long id) {
        requireTrunk(companyId, id);
        repository.makeDefault(companyId, id);
        audit.record(companyId, "SIP_TRUNK_SET_DEFAULT", "sip_trunk", String.valueOf(id), null);
        return requireTrunk(companyId, id);
    }

    @Override
    public void delete(long companyId, long id) {
        SipTrunk trunk = requireSipTrunk(companyId, id);
        if (trunk.isDefault()) {
            throw new ConflictException(ErrorCode.SIP_TRUNK_DEFAULT_DELETE_FORBIDDEN);
        }
        repository.delete(companyId, id);
        pjsipConfig.regenerateAndReload();
        audit.record(companyId, "SIP_TRUNK_DELETE", "sip_trunk", String.valueOf(id), trunk.name());
    }

    @Override
    public PageableData<SipTrunkRow> list(long companyId, SipTrunkFilter filter) {
        List<SipTrunkRow> rows = repository.findAll(companyId, filter).stream().map(SipTrunkRow::of).toList();
        long total = repository.count(companyId, filter);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    @Override
    public SipTrunkRow requireTrunk(long companyId, long id) {
        return SipTrunkRow.of(requireSipTrunk(companyId, id));
    }

    private SipTrunk requireSipTrunk(long companyId, long id) {
        SipTrunk trunk = repository.find(companyId, id);
        if (trunk == null) {
            throw new NotFoundException(ErrorCode.SIP_TRUNK_NOT_FOUND, id);
        }
        return trunk;
    }

    @Override
    public SipTrunkRow findDefaultForCall(long companyId) {
        SipTrunk trunk = repository.findDefaultForCompany(companyId);
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
        List<SipTrunk> matched = repository.findEnabledByIdsAndCompany(candidateTrunkIds, companyId);
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
        List<SipTrunk> enabled = repository.findAllEnabledByCompany(companyId);
        if (enabled.isEmpty()) {
            SipTrunk def = repository.findDefaultForCompany(companyId);
            if (def != null) {
                return List.of(SipTrunkRow.of(def));
            }
            return List.of();
        }
        return enabled.stream().map(SipTrunkRow::of).toList();
    }

    @Override
    public SipTrunkStatus getStatus(long companyId, long id) {
        SipTrunk trunk = requireSipTrunk(companyId, id);
        return evaluateTrunkStatus(trunk);
    }

    @Override
    public List<SipTrunkStatus> getAllStatuses(long companyId) {
        List<SipTrunk> trunks = repository.findAll(companyId, new SipTrunkFilter(0, 100, null));
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

    private String generatedEndpoint(long companyId, long id) {
        return "trunk_" + companyId + "_" + id;
    }

    private static String pendingEndpoint() {
        return "pending";
    }
}
