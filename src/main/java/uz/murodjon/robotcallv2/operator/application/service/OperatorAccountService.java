package uz.murodjon.robotcallv2.operator.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.ari.WebTestProperties;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorRow;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorSipAccountRow;
import uz.murodjon.robotcallv2.operator.application.dto.SaveOperatorSipAccountRequest;
import uz.murodjon.robotcallv2.operator.application.dto.SipCredentialsResponse;
import uz.murodjon.robotcallv2.operator.application.dto.UpdateOperatorStatusRequest;
import uz.murodjon.robotcallv2.operator.application.port.input.ContactCenterEventUseCase;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorAccountUseCase;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorPresenceUseCase;
import uz.murodjon.robotcallv2.operator.application.port.output.OperatorEndpointConfigPort;
import uz.murodjon.robotcallv2.operator.application.port.output.OperatorSipAccountRepository;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorFilter;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorPresence;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorSipAccount;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorSipEndpoint;
import uz.murodjon.robotcallv2.operator.domain.enums.OperatorStatus;
import uz.murodjon.robotcallv2.operator.domain.enums.OperatorTableField;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.stream.EventSink;
import uz.murodjon.robotcallv2.shared.stream.EventSubscription;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;
import uz.murodjon.robotcallv2.shared.util.SecretCipher;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.user.domain.entity.User;
import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Operators as telephony endpoints (docs/INBOUND-PBX-PLAN.md §7.1–7.2): the SIP account an
 * admin gives a user, the credentials the operator's softphone signs in with, and the
 * operator's own status.
 */
@Service
public class OperatorAccountService implements OperatorAccountUseCase {

    private static final Logger log = LoggerFactory.getLogger(OperatorAccountService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    /** No look-alike characters: a desk phone's password is typed in by hand. */
    private static final String PASSWORD_ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int PASSWORD_LENGTH = 20;
    private static final Pattern OPERATOR_ENDPOINT = Pattern.compile("op-(\\d+)");

    private final OperatorSipAccountRepository accountRepository;
    private final OperatorEndpointConfigPort endpointConfigPort;
    private final UserUseCase userUseCase;
    private final SecretCipher secretCipher;
    private final OperatorPresenceUseCase operatorPresenceUseCase;
    private final ContactCenterEventUseCase contactCenterEventUseCase;
    private final CurrentUser currentUser;
    private final WebTestProperties webTestProperties;
    private final AuditUseCase auditService;

    public OperatorAccountService(OperatorSipAccountRepository accountRepository,
                                  OperatorEndpointConfigPort endpointConfigPort, UserUseCase userUseCase,
                                  SecretCipher secretCipher, OperatorPresenceUseCase operatorPresenceUseCase,
                                  ContactCenterEventUseCase contactCenterEventUseCase, CurrentUser currentUser,
                                  WebTestProperties webTestProperties, AuditUseCase auditService) {
        this.accountRepository = accountRepository;
        this.endpointConfigPort = endpointConfigPort;
        this.userUseCase = userUseCase;
        this.secretCipher = secretCipher;
        this.operatorPresenceUseCase = operatorPresenceUseCase;
        this.contactCenterEventUseCase = contactCenterEventUseCase;
        this.currentUser = currentUser;
        this.webTestProperties = webTestProperties;
        this.auditService = auditService;
    }

    @Override
    public OperatorSipAccountRow saveSipAccount(long companyId, long userId, SaveOperatorSipAccountRequest request) {
        User user = userUseCase.requireUser(companyId, userId);
        boolean webrtc = request.webrtc() == null || request.webrtc();
        String forwardNumber = request.forwardNumber() == null || request.forwardNumber().isBlank()
                ? null : PhoneNumbers.require(request.forwardNumber());
        OperatorSipAccount existing = accountRepository.find(companyId, userId);
        if (existing == null) {
            accountRepository.create(companyId, new OperatorSipAccount(userId, companyId,
                    OperatorSipAccount.endpointNameFor(userId), secretCipher.encrypt(generatePassword()),
                    webrtc, forwardNumber, null, null));
        } else {
            accountRepository.update(companyId, userId, webrtc, forwardNumber);
        }
        regenerateEndpoints();
        auditService.record(companyId, existing == null ? "OPERATOR_SIP_ACCOUNT_CREATE" : "OPERATOR_SIP_ACCOUNT_UPDATE",
                "operator_sip_account", String.valueOf(userId), webrtc ? "webrtc" : "sip");
        return toRow(user, requireAccount(companyId, userId));
    }

    @Override
    public SipCredentialsResponse resetSipPassword(long companyId, long userId) {
        User user = userUseCase.requireUser(companyId, userId);
        OperatorSipAccount account = requireAccount(companyId, userId);
        String password = generatePassword();
        accountRepository.updatePassword(companyId, userId, secretCipher.encrypt(password));
        regenerateEndpoints();
        auditService.record(companyId, "OPERATOR_SIP_PASSWORD_RESET", "operator_sip_account", String.valueOf(userId), null);
        return new SipCredentialsResponse(account.endpointName(), password, webTestProperties.wsUrl(),
                user.sipExtension(), account.webrtc());
    }

    @Override
    public void deleteSipAccount(long companyId, long userId) {
        requireAccount(companyId, userId);
        accountRepository.delete(companyId, userId);
        operatorPresenceUseCase.setStatus(companyId, userId, OperatorStatus.OFFLINE, null);
        regenerateEndpoints();
        auditService.record(companyId, "OPERATOR_SIP_ACCOUNT_DELETE", "operator_sip_account", String.valueOf(userId), null);
    }

    @Override
    public PageableData<OperatorRow> listOperators(long companyId, OperatorFilter filter) {
        List<OperatorRow> rows = new ArrayList<>();
        for (OperatorSipAccount account : accountRepository.findByCompanyId(companyId)) {
            User user;
            try {
                user = userUseCase.requireUser(companyId, account.userId());
            } catch (NotFoundException e) {
                continue;
            }
            OperatorPresence presence = operatorPresenceUseCase.find(companyId, account.userId());
            if (filter.status() != null && presence.status() != filter.status()) {
                continue;
            }
            rows.add(new OperatorRow(user.id(), user.name(), user.sipExtension(), presence.status(), presence.reason(),
                    presence.since(), presence.channelId(), presence.callsToday(), account.webrtc(),
                    account.forwardNumber()));
        }
        rows.sort(comparatorFor(filter));
        int from = (int) Math.min(filter.offset(), rows.size());
        int to = Math.min(from + filter.sizeOrDefault(), rows.size());
        return PageableData.of(rows.subList(from, to), filter.pageOrDefault(), filter.sizeOrDefault(), rows.size());
    }

    @Override
    public SipCredentialsResponse findMyCredentials(long companyId) {
        long userId = requireUserId();
        User user = userUseCase.requireUser(companyId, userId);
        OperatorSipAccount account = requireAccount(companyId, userId);
        return new SipCredentialsResponse(account.endpointName(), secretCipher.decrypt(account.passwordEnc()),
                webTestProperties.wsUrl(), user.sipExtension(), account.webrtc());
    }

    @Override
    public OperatorPresence findMyStatus(long companyId) {
        return operatorPresenceUseCase.find(companyId, requireUserId());
    }

    @Override
    public OperatorPresence updateMyStatus(long companyId, UpdateOperatorStatusRequest request) {
        long userId = requireUserId();
        if (!request.status().selectableByOperator()) {
            throw new ValidationException(ErrorCode.OPERATOR_STATUS_NOT_SELECTABLE, request.status());
        }
        if (request.status() == OperatorStatus.AVAILABLE) {
            requireAccount(companyId, userId);
        }
        OperatorStatus current = operatorPresenceUseCase.find(companyId, userId).status();
        if (current == OperatorStatus.RINGING || current == OperatorStatus.ON_CALL) {
            throw new ConflictException(ErrorCode.OPERATOR_ON_CALL);
        }
        return operatorPresenceUseCase.setStatus(companyId, userId, request.status(), request.reason());
    }

    @Override
    public EventSubscription subscribeMyEvents(long companyId, EventSink sink) {
        return contactCenterEventUseCase.subscribeUser(companyId, requireUserId(), sink);
    }

    @Override
    public OperatorSipAccount findSipAccount(long companyId, long userId) {
        return accountRepository.find(companyId, userId);
    }

    @Override
    public Long findUserIdByEndpoint(String endpointName) {
        if (endpointName == null) {
            return null;
        }
        Matcher matcher = OPERATOR_ENDPOINT.matcher(endpointName);
        return matcher.matches() ? Long.parseLong(matcher.group(1)) : null;
    }

    @Override
    public OperatorSipAccount findSipAccountByEndpoint(String endpointName) {
        Long userId = findUserIdByEndpoint(endpointName);
        return userId != null ? accountRepository.findByUserId(userId) : null;
    }

    /** The file Asterisk reads survives a restart, but a key or a user may have changed meanwhile. */
    @EventListener(ApplicationReadyEvent.class)
    public void writeEndpointsOnStartup() {
        try {
            regenerateEndpoints();
        } catch (Exception e) {
            log.warn("Could not write operator endpoints at startup: {}", e.getMessage());
        }
    }

    /**
     * Rewrites every company's operator endpoints. A blocked user's endpoint is left out, so
     * blocking someone in the panel also signs their phone out of Asterisk.
     */
    private void regenerateEndpoints() {
        List<OperatorSipEndpoint> endpoints = new ArrayList<>();
        for (OperatorSipAccount account : accountRepository.findAll()) {
            try {
                User user = userUseCase.requireUser(account.companyId(), account.userId());
                if (user.status() == UserStatus.BLOCKED) {
                    continue;
                }
                endpoints.add(new OperatorSipEndpoint(account.userId(), account.companyId(), account.endpointName(),
                        secretCipher.decrypt(account.passwordEnc()), account.webrtc(), user.name(), user.sipExtension()));
            } catch (Exception e) {
                log.warn("Leaving operator endpoint {} out of the Asterisk config: {}", account.endpointName(), e.getMessage());
            }
        }
        endpointConfigPort.writeEndpoints(endpoints);
    }

    private OperatorSipAccount requireAccount(long companyId, long userId) {
        OperatorSipAccount account = accountRepository.find(companyId, userId);
        if (account == null) {
            throw new NotFoundException(ErrorCode.OPERATOR_SIP_ACCOUNT_NOT_FOUND, userId);
        }
        return account;
    }

    private long requireUserId() {
        return currentUser.id().orElseThrow(() -> new ForbiddenException(ErrorCode.NO_USER_SESSION));
    }

    private static OperatorSipAccountRow toRow(User user, OperatorSipAccount account) {
        return new OperatorSipAccountRow(user.id(), user.name(), user.sipExtension(), account.endpointName(),
                account.webrtc(), account.forwardNumber(), account.createdAt());
    }

    private static Comparator<OperatorRow> comparatorFor(OperatorFilter filter) {
        Map.Entry<OperatorTableField, org.springframework.data.domain.Sort.Direction> first =
                filter.orders().entrySet().iterator().next();
        Comparator<OperatorRow> comparator = switch (first.getKey()) {
            case USER_ID -> Comparator.comparingLong(OperatorRow::userId);
            case STATUS -> Comparator.comparing(OperatorRow::status);
            case NAME -> Comparator.comparing(row -> row.name() == null ? "" : row.name(), String.CASE_INSENSITIVE_ORDER);
        };
        return first.getValue().isDescending() ? comparator.reversed() : comparator;
    }

    private static String generatePassword() {
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        return password.toString();
    }
}
