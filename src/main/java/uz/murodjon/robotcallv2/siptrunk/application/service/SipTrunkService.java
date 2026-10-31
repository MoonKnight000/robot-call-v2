package uz.murodjon.robotcallv2.siptrunk.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.agent.ami.AmiClient;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.*;
import uz.murodjon.robotcallv2.shared.util.SecretCipher;
import uz.murodjon.robotcallv2.siptrunk.application.dto.*;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.SipTrunkUseCase;
import uz.murodjon.robotcallv2.siptrunk.application.port.output.SipTrunkRepository;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunk;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunkCredentials;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunkFilter;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.SipTrunkTransport;
import uz.murodjon.robotcallv2.siptrunk.domain.service.SipTrunkValidator;

import java.time.Instant;
import java.util.List;

/**
 * SIP trunk CRUD and live status monitoring (ROADMAP B.3, report #7).
 */
@Service
public class SipTrunkService implements SipTrunkUseCase {

    private static final Logger log = LoggerFactory.getLogger(SipTrunkService.class);

    private final SipTrunkRepository repository;
    private final PjsipConfigWriter pjsipConfigWriter;
    private final SecretCipher cipher;
    private final AuditUseCase audit;
    private final AmiClient ami;
    private final CurrentUser currentUser;

    public SipTrunkService(SipTrunkRepository repository, PjsipConfigWriter pjsipConfigWriter, SecretCipher cipher,
                           AuditUseCase audit, AmiClient ami, CurrentUser currentUser) {
        this.repository = repository;
        this.pjsipConfigWriter = pjsipConfigWriter;
        this.cipher = cipher;
        this.audit = audit;
        this.ami = ami;
        this.currentUser = currentUser;
    }

    @Override
    @Transactional
    public SipTrunkRow create(long companyId, CreateSipTrunkRequest request) {
        SipTrunkValidator.requireValidCallerId(request.callerId());
        SipTrunkValidator.requireSupportedCodecs(request.codecs());
        long id;
        if (SipTrunkValidator.isManaged(request.pjsipEndpoint(), request.host())) {
            SipTrunkValidator.requireValidHost(request.host());
            SipTrunkValidator.requireUsername(request.sipUsername());
            SipTrunkValidator.requireValidTransport(request.transport());
            if (request.sipPassword() == null || request.sipPassword().isBlank()) {
                throw new ValidationException(ErrorCode.SIP_TRUNK_PASSWORD_REQUIRED);
            }
            SipTrunkValidator.requireValidPassword(request.sipPassword());
            id = repository.create(companyId, request.name(), pendingEndpoint(), request.callerId(), false,
                    request.host(), SipTrunkValidator.portOrDefault(request.port()), request.sipUsername(), encryptPassword(request.sipPassword()),
                    SipTrunkValidator.transportOrDefault(request.transport()), request.codecs(), request.maxCps(), request.maxChannels());
            repository.updatePjsipEndpoint(companyId, id, generatedEndpoint(companyId, id));
        } else {
            requireManualEndpointAllowed(request.pjsipEndpoint(), null);
            id = repository.create(companyId, request.name(), request.pjsipEndpoint(), request.callerId(), false,
                    null, 5060, null, null, SipTrunkTransport.UDP, request.codecs(), request.maxCps(), request.maxChannels());
        }
        pjsipConfigWriter.regenerateAndReload();
        audit.record(companyId, "SIP_TRUNK_CREATE", "sip_trunk", String.valueOf(id), request.name());
        return requireTrunk(companyId, id);
    }

    @Override
    public SipTrunkRow update(long companyId, long id, UpdateSipTrunkRequest request) {
        SipTrunk existing = requireSipTrunk(companyId, id);
        SipTrunkValidator.requireValidCallerId(request.callerId());
        SipTrunkValidator.requireSupportedCodecs(request.codecs());
        if (SipTrunkValidator.isManaged(request.pjsipEndpoint(), request.host())) {
            SipTrunkValidator.requireValidHost(request.host());
            SipTrunkValidator.requireUsername(request.sipUsername());
            SipTrunkValidator.requireValidTransport(request.transport());
            String passwordEnc;
            if (request.sipPassword() != null && !request.sipPassword().isBlank()) {
                SipTrunkValidator.requireValidPassword(request.sipPassword());
                passwordEnc = encryptPassword(request.sipPassword());
            } else if (existing.host() != null) {
                passwordEnc = null; // repository.update keeps the trunk's current encrypted password
            } else {
                throw new ValidationException(ErrorCode.SIP_TRUNK_PASSWORD_REQUIRED_ON_SWITCH);
            }
            repository.update(companyId, id, request.name(), generatedEndpoint(companyId, id), request.callerId(), request.enabled(),
                    request.host(), SipTrunkValidator.portOrDefault(request.port()), request.sipUsername(), passwordEnc,
                    SipTrunkValidator.transportOrDefault(request.transport()), request.codecs(), request.maxCps(), request.maxChannels());
        } else {
            requireManualEndpointAllowed(request.pjsipEndpoint(), existing.pjsipEndpoint());
            repository.update(companyId, id, request.name(), request.pjsipEndpoint(), request.callerId(), request.enabled(),
                    null, 5060, null, null, SipTrunkTransport.UDP, request.codecs(), request.maxCps(), request.maxChannels());
        }
        pjsipConfigWriter.regenerateAndReload();
        audit.record(companyId, "SIP_TRUNK_UPDATE", "sip_trunk", String.valueOf(id), request.name());
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
    public SipTrunkDeleteResponse delete(long companyId, long id) {
        SipTrunk trunk = requireSipTrunk(companyId, id);
        if (trunk.isDefault()) {
            throw new ConflictException(ErrorCode.SIP_TRUNK_DEFAULT_DELETE_FORBIDDEN);
        }
        repository.delete(companyId, id);
        pjsipConfigWriter.regenerateAndReload();
        audit.record(companyId, "SIP_TRUNK_DELETE", "sip_trunk", String.valueOf(id), trunk.name());
        return new SipTrunkDeleteResponse(id, true);
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

    @Override
    public SipTrunkCredentials requireCredentialsForCall(long companyId, long id) {
        SipTrunk trunk = requireSipTrunk(companyId, id);
        if (trunk.host() == null || trunk.sipPasswordEnc() == null) {
            throw new ConflictException(ErrorCode.SIP_TRUNK_NOT_MANAGED, id);
        }
        return new SipTrunkCredentials(trunk.id(), trunk.host(), trunk.port(), trunk.transport(), trunk.sipUsername(),
                cipher.decrypt(trunk.sipPasswordEnc()));
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
    public SipTrunkStatus findStatus(long companyId, long id) {
        SipTrunk trunk = requireSipTrunk(companyId, id);
        return evaluateTrunkStatus(trunk);
    }

    @Override
    public List<SipTrunkStatus> findAllStatuses(long companyId) {
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

    /**
     * A manual endpoint is a name the dialer puts straight into {@code PJSIP/<number>@<name>},
     * and it can name any endpoint Asterisk knows — the platform's own trunk or another
     * company's generated {@code trunk_<companyId>_<id>}. Letting a tenant pick it meant
     * dialling out on someone else's SIP account and bill, so only a platform admin may set
     * or change one; a tenant can still edit the rest of a trunk an admin set up this way.
     */
    private void requireManualEndpointAllowed(String pjsipEndpoint, String currentEndpoint) {
        SipTrunkValidator.requireValidEndpointName(pjsipEndpoint);
        if (!pjsipEndpoint.equals(currentEndpoint) && !currentUser.hasPermission(Permission.PLATFORM_ADMIN)) {
            throw new ForbiddenException(ErrorCode.SIP_TRUNK_MANUAL_ENDPOINT_FORBIDDEN);
        }
    }

    private String encryptPassword(String plaintext) {
        if (!cipher.available()) {
            throw new ConflictException(ErrorCode.ENCRYPTION_KEY_NOT_SET);
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
