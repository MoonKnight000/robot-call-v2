package uz.murodjon.robotcallv2.agent.ari;

import ch.loway.oss.ari4java.generated.models.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.dialog.DialogProperties;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.callrecord.application.dto.CallOriginateResponse;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallAttempt;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;
import uz.murodjon.robotcallv2.company.infrastructure.config.CompanyProperties;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.dialer.application.port.input.OutboundCallTrackingUseCase;
import uz.murodjon.robotcallv2.donotcall.application.port.input.DoNotCallUseCase;
import uz.murodjon.robotcallv2.scenario.application.port.input.ScenarioUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;
import uz.murodjon.robotcallv2.siptrunk.application.dto.SipTrunkRow;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.SipTrunkUseCase;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Every call this application dials out: campaign calls, manual and test calls, and the
 * outside numbers the PBX side rings. Picks the trunk, endpoint and caller id, and applies
 * the company's checks (suspension, balance, do-not-call list) before Asterisk is asked.
 */
@Service
class OutboundDialService {

    private static final Logger log = LoggerFactory.getLogger(OutboundDialService.class);

    private final AsteriskProperties asteriskProperties;
    private final AriClient ariClient;
    private final CallAttemptUseCase callAttemptUseCase;
    private final OutboundCallTrackingUseCase outboundCallTrackingUseCase;
    private final DialogProperties dialogProperties;
    private final SipTrunkUseCase sipTrunkUseCase;
    private final DoNotCallUseCase doNotCallUseCase;
    private final CompanyUseCase companyUseCase;
    private final CallBillingUseCase callBillingUseCase;
    /** Whose calls may go out over the platform's own trunk. */
    private final CompanyProperties companyProperties;
    private final ScenarioUseCase scenarioUseCase;
    private final AiAgentUseCase aiAgentUseCase;
    private final AuditUseCase audit;
    private final PendingCallRegistry pendingCallRegistry;
    private final TrunkCallPacer trunkCallPacer;

    OutboundDialService(AsteriskProperties asteriskProperties,
                        AriClient ariClient,
                        CallAttemptUseCase callAttemptUseCase,
                        OutboundCallTrackingUseCase outboundCallTrackingUseCase,
                        DialogProperties dialogProperties,
                        SipTrunkUseCase sipTrunkUseCase,
                        DoNotCallUseCase doNotCallUseCase,
                        CompanyUseCase companyUseCase,
                        CallBillingUseCase callBillingUseCase,
                        CompanyProperties companyProperties,
                        ScenarioUseCase scenarioUseCase,
                        AiAgentUseCase aiAgentUseCase,
                        AuditUseCase audit,
                        PendingCallRegistry pendingCallRegistry,
                        TrunkCallPacer trunkCallPacer) {
        this.asteriskProperties = asteriskProperties;
        this.ariClient = ariClient;
        this.callAttemptUseCase = callAttemptUseCase;
        this.outboundCallTrackingUseCase = outboundCallTrackingUseCase;
        this.dialogProperties = dialogProperties;
        this.sipTrunkUseCase = sipTrunkUseCase;
        this.doNotCallUseCase = doNotCallUseCase;
        this.companyUseCase = companyUseCase;
        this.callBillingUseCase = callBillingUseCase;
        this.companyProperties = companyProperties;
        this.scenarioUseCase = scenarioUseCase;
        this.aiAgentUseCase = aiAgentUseCase;
        this.audit = audit;
        this.pendingCallRegistry = pendingCallRegistry;
        this.trunkCallPacer = trunkCallPacer;
    }

    String originate(String rawNumber, long companyId) {
        return originate(rawNumber, companyId, (Long) null);
    }

    String originate(String rawNumber, long companyId, OutboundCall outboundCall) {
        Long trunkId = outboundCall != null ? outboundCall.sipTrunkId() : null;
        return originate(rawNumber, companyId, trunkId, outboundCall);
    }

    String originate(String rawNumber, long companyId, Long sipTrunkId) {
        return originate(rawNumber, companyId, sipTrunkId, null);
    }

    private String originate(String rawNumber, long companyId, Long sipTrunkId, OutboundCall outboundCall) {
        String number = PhoneNumbers.require(rawNumber);
        if (outboundCall == null && !isLocalNumber(number)) {
            requireDialAllowed(companyId, rawNumber, number);
        }
        String callId = "call-" + System.currentTimeMillis();
        long targetId = outboundCall != null ? outboundCall.targetId() : CallAttempt.NO_TARGET;
        // No OutboundCall means a manual/test originate, and StasisStart drives exactly
        // those with dialogProperties.language(). Reading it here too keeps the row and the
        // conversation on one language — the row is written now and never rewritten, so a
        // literal here left the call held in uz-UZ but reported as "uz".
        String language = outboundCall != null ? outboundCall.language() : dialogProperties.language();
        SipTrunkRow trunk;
        try {
            trunk = isLocalNumber(number) ? null : resolveTrunkForCall(companyId, sipTrunkId);
        } catch (RuntimeException e) {
            // Refused before Asterisk was asked, but the dialer has already counted the
            // attempt: it still leaves a row saying why.
            callAttemptUseCase.recordUnplacedAttempt(companyId, targetId, number, language,
                    Disposition.FAILED, "no trunk for the call: " + e.getMessage());
            throw e;
        }
        String endpoint = endpointFor(number, trunk);
        String callerId = callerIdFor(trunk);
        String dialNumber = !isLocalNumber(number) && number.startsWith("+")
                ? number.substring(1)
                : number;
        awaitTrunkSlot(number, endpoint, trunk);
        try {
            // Inside the try on purpose: an ARI connection that is down is one more way
            // for the call never to happen, and it has to leave a row like the rest.
            var request = ariClient.requireConnection().channels()
                    .originate("PJSIP/" + dialNumber + "@" + endpoint)
                    .setApp(asteriskProperties.appName())
                    .setAppArgs(callId)
                    .setTimeout(asteriskProperties.answerTimeoutSec());
            if (callerId != null && !callerId.isBlank()) {
                request.setCallerId(callerId);
            }
            Channel channel = request.execute();
            String channelId = channel.getId();
            callAttemptUseCase.startAttempt(companyId, targetId, channelId, number, language, null,
                    outboundCall != null ? outboundCall.variantId() : null);
            if (outboundCall != null) {
                outboundCallTrackingUseCase.register(channelId, outboundCall);
            }
            log.info("Originated call {} to {} (dial={}) via {} (trunkId={}) -> channel {}",
                    callId, PhoneMasker.mask(number), PhoneMasker.mask(dialNumber), endpoint, sipTrunkId, channelId);
            return channelId;
        } catch (Exception e) {
            // Asterisk never took the call, so no channel and no ChannelDestroyed will
            // ever arrive to close a row — write the closed row here instead, or the
            // attempt the dialer already counted leaves no trace of why it failed.
            callAttemptUseCase.recordUnplacedAttempt(companyId, targetId, number, language,
                    Disposition.FAILED, "originate via " + endpoint + " failed: " + e.getMessage());
            throw new ExternalServiceException(ErrorCode.ARI_ORIGINATE_FAILED, "asterisk", e,
                    number, endpoint, e.getMessage());
        }
    }

    /**
     * Every outside number dialled on a company's behalf passes here — except the campaign
     * dialer's own calls, which CallTaskConsumer has already checked against the
     * do-not-call list and held money for, and which it records as attempts when refused.
     * Manual and test calls, operator callbacks, queue forwards and consult legs used to
     * skip all three checks: a suspended or unpaid company kept dialling, and a number on
     * the do-not-call list could still be rung by hand.
     */
    private void requireDialAllowed(long companyId, String rawNumber, String number) {
        requireCompanyMayCall(companyId);
        // do_not_call.phone is stored as typed, so the normalised form alone would miss
        // "998901234567" or " +998 90 ..." entries.
        Set<String> forms = new LinkedHashSet<>(List.of(number, number.startsWith("+") ? number.substring(1) : number,
                rawNumber.trim()));
        if (forms.stream().anyMatch(form -> doNotCallUseCase.contains(companyId, form))) {
            throw new ConflictException(ErrorCode.CALL_NUMBER_ON_DO_NOT_CALL_LIST, number);
        }
    }

    /** A suspended company, or one without the money for a call, starts no call of any kind — browser ones included. */
    void requireCompanyMayCall(long companyId) {
        Company company = companyUseCase.findById(companyId);
        if (company != null && company.status() == CompanyStatus.SUSPENDED) {
            throw new ForbiddenException(ErrorCode.COMPANY_SUSPENDED);
        }
        if (!callBillingUseCase.hasBalanceForCall(companyId)) {
            throw new ConflictException(ErrorCode.CALL_BALANCE_INSUFFICIENT);
        }
    }

    CallOriginateResponse originateManualCall(long companyId, String number, Long scenarioId) {
        return originateManualCall(companyId, number, scenarioId, null, null);
    }

    CallOriginateResponse originateManualCall(long companyId, String number, Long scenarioId, Long agentId,
                                              Long sipTrunkId) {
        if (scenarioId != null) {
            scenarioUseCase.requireScenario(companyId, scenarioId);
        }
        // Loaded before dialling, so an unknown agent is a 404 rather than a phone that rings for nothing.
        AiAgent agent = agentId != null ? aiAgentUseCase.requireCallAgent(companyId, agentId, number) : null;
        String channelId = originate(number, companyId, sipTrunkId);
        if (agent != null) {
            pendingCallRegistry.registerManualAgent(channelId, agent, companyId);
        } else {
            pendingCallRegistry.registerManualScenario(channelId, scenarioId, companyId);
        }
        audit.record(companyId, "CALL_ORIGINATE_MANUAL", "call", channelId, number);
        return new CallOriginateResponse(number, channelId);
    }

    CallOriginateResponse originateTestCall(long companyId, String number, ScenarioDefinition definition) {
        return originateTestCall(companyId, number, definition, null);
    }

    CallOriginateResponse originateTestCall(long companyId, String number, ScenarioDefinition definition,
                                            Long sipTrunkId) {
        String channelId = originate(number, companyId, sipTrunkId);
        pendingCallRegistry.registerManualDefinition(channelId, definition, companyId);
        audit.record(companyId, "CALL_ORIGINATE_TEST", "call", channelId, number);
        return new CallOriginateResponse(number, channelId);
    }

    private SipTrunkRow resolveTrunkForCall(long companyId, Long sipTrunkId) {
        if (sipTrunkId != null) {
            // Used or refused: dialling it over another trunk showed the callee a caller ID nobody chose.
            return sipTrunkUseCase.requireTrunk(companyId, sipTrunkId);
        }
        SipTrunkRow trunk = sipTrunkUseCase.findDefaultForCall(companyId);
        if (trunk == null) {
            requirePlatformTrunkAllowed(companyId);
        }
        return trunk;
    }

    /**
     * The platform's own trunk carries the platform company's calls only. Any other company
     * without a trunk of its own is refused rather than dialled out on the platform's SIP
     * account and caller ID.
     */
    void requirePlatformTrunkAllowed(long companyId) {
        if (companyId != companyProperties.defaultId()) {
            throw new ConflictException(ErrorCode.SIP_TRUNK_NONE_CONFIGURED, companyId);
        }
    }

    private boolean isLocalNumber(String number) {
        String local = asteriskProperties.localEndpoint();
        String pattern = asteriskProperties.localNumberPattern();
        return local != null && !local.isBlank()
                && pattern != null && !pattern.isBlank()
                && number != null && number.matches(pattern);
    }

    private String endpointFor(String number, SipTrunkRow trunk) {
        if (isLocalNumber(number)) {
            return asteriskProperties.localEndpoint();
        }
        return trunk != null ? trunk.pjsipEndpoint() : asteriskProperties.trunkEndpoint();
    }

    /** Under the trunk's calls-per-second limit; the local softphone has none. */
    private void awaitTrunkSlot(String number, String endpoint, SipTrunkRow trunk) {
        if (!isLocalNumber(number)) {
            trunkCallPacer.awaitSlot(endpoint, trunk != null ? trunk.maxCps() : null);
        }
    }

    private String callerIdFor(SipTrunkRow trunk) {
        if (trunk != null && trunk.callerId() != null && !trunk.callerId().isBlank()) {
            return trunk.callerId();
        }
        return asteriskProperties.callerId();
    }

    /** The same for an outside number, over the company's trunk and with its caller id. */
    String originateExternalToStasis(long companyId, String rawNumber, String appArgs, int timeoutSec) {
        String number = PhoneNumbers.require(rawNumber);
        if (!isLocalNumber(number)) {
            requireDialAllowed(companyId, rawNumber, number);
        }
        SipTrunkRow trunk = isLocalNumber(number) ? null : resolveTrunkForCall(companyId, null);
        String endpoint = endpointFor(number, trunk);
        String dialNumber = !isLocalNumber(number) && number.startsWith("+") ? number.substring(1) : number;
        awaitTrunkSlot(number, endpoint, trunk);
        return ariClient.originateToStasis("PJSIP/" + dialNumber + "@" + endpoint, appArgs, timeoutSec, callerIdFor(trunk));
    }

    /** An outside number dialled for an operator (a callback, a softphone call, a preview card) — booked as a call from the start. */
    String originateClientCall(long companyId, String rawNumber, String appArgs, int timeoutSec, String language,
                               long targetId) {
        String number = PhoneNumbers.require(rawNumber);
        String channelId = originateExternalToStasis(companyId, number, appArgs, timeoutSec);
        callAttemptUseCase.startAttempt(companyId, targetId, channelId, number, language, null, null);
        return channelId;
    }
}
