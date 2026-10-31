package uz.murodjon.robotcallv2.agent.ari;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.dialog.DialogProperties;
import uz.murodjon.robotcallv2.agent.telephony.OutboundDial;
import uz.murodjon.robotcallv2.agent.telephony.TelephonyGatewayResolver;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.callrecord.application.dto.CallOriginateResponse;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallAttempt;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.role.domain.enums.Feature;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.donotcall.application.port.input.DoNotCallUseCase;
import uz.murodjon.robotcallv2.scenario.application.port.input.ScenarioUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.TelephonyEngine;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;
import uz.murodjon.robotcallv2.siptrunk.application.dto.SipTrunkRow;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Every call this application dials out: campaign calls, manual and test calls, and the
 * outside numbers the PBX side rings. Applies the company's checks (suspension, balance,
 * do-not-call list) and hands the call to the company's telephony engine to place.
 */
@Service
class OutboundDialService {

    private final AriClient ariClient;
    private final CallAttemptUseCase callAttemptUseCase;
    private final DialogProperties dialogProperties;
    private final DoNotCallUseCase doNotCallUseCase;
    private final CompanyUseCase companyUseCase;
    private final CallBillingUseCase callBillingUseCase;
    private final ScenarioUseCase scenarioUseCase;
    private final AiAgentUseCase aiAgentUseCase;
    private final AuditUseCase audit;
    private final PendingCallRegistry pendingCallRegistry;
    private final CompanyConfigUseCase companyConfigUseCase;
    private final OutboundRouting outboundRouting;
    private final TelephonyGatewayResolver telephonyGatewayResolver;

    OutboundDialService(AriClient ariClient,
                        CallAttemptUseCase callAttemptUseCase,
                        DialogProperties dialogProperties,
                        DoNotCallUseCase doNotCallUseCase,
                        CompanyUseCase companyUseCase,
                        CallBillingUseCase callBillingUseCase,
                        ScenarioUseCase scenarioUseCase,
                        AiAgentUseCase aiAgentUseCase,
                        AuditUseCase audit,
                        PendingCallRegistry pendingCallRegistry,
                        CompanyConfigUseCase companyConfigUseCase,
                        OutboundRouting outboundRouting,
                        TelephonyGatewayResolver telephonyGatewayResolver) {
        this.ariClient = ariClient;
        this.callAttemptUseCase = callAttemptUseCase;
        this.dialogProperties = dialogProperties;
        this.doNotCallUseCase = doNotCallUseCase;
        this.companyUseCase = companyUseCase;
        this.callBillingUseCase = callBillingUseCase;
        this.scenarioUseCase = scenarioUseCase;
        this.aiAgentUseCase = aiAgentUseCase;
        this.audit = audit;
        this.pendingCallRegistry = pendingCallRegistry;
        this.companyConfigUseCase = companyConfigUseCase;
        this.outboundRouting = outboundRouting;
        this.telephonyGatewayResolver = telephonyGatewayResolver;
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
        if (!outboundRouting.isLocalNumber(number)) {
            if (outboundCall == null) {
                requireDialAllowed(companyId, rawNumber, number);
            } else {
                requireNotOnDoNotCallList(companyId, rawNumber, number);
            }
        }
        String callId = "call-" + System.currentTimeMillis();
        long targetId = outboundCall != null ? outboundCall.targetId() : CallAttempt.NO_TARGET;
        // No OutboundCall means a manual/test originate, and StasisStart drives exactly
        // those with dialogProperties.language(). Reading it here too keeps the row and the
        // conversation on one language — the row is written now and never rewritten, so a
        // literal here left the call held in uz-UZ but reported as "uz".
        String language = outboundCall != null ? outboundCall.language() : dialogProperties.language();

        CompanyConfig config = companyConfigUseCase.find(companyId);
        TelephonyEngine engine = config != null ? config.telephonyEngine() : TelephonyEngine.ASTERISK;
        return telephonyGatewayResolver.findByEngine(engine)
                .originate(new OutboundDial(companyId, targetId, number, language, sipTrunkId, outboundCall, callId));
    }

    /**
     * Every outside number dialled on a company's behalf passes here. Campaign calls are
     * checked here again after the dialer's earlier check, closing the queue/preparation gap.
     * Manual and test calls, operator callbacks, queue forwards and consult legs used to
     * skip all three checks: a suspended or unpaid company kept dialling, and a number on
     * the do-not-call list could still be rung by hand.
     */
    private void requireDialAllowed(long companyId, String rawNumber, String number) {
        requireCompanyMayCall(companyId);
        requireNotOnDoNotCallList(companyId, rawNumber, number);
    }

    private void requireNotOnDoNotCallList(long companyId, String rawNumber, String number) {
        // do_not_call.phone is stored as typed, so the normalised form alone would miss
        // "998901234567" or " +998 90 ..." entries.
        Set<String> forms = new LinkedHashSet<>(List.of(number, number.startsWith("+") ? number.substring(1) : number,
                rawNumber.trim()));
        if (forms.stream().anyMatch(form -> doNotCallUseCase.contains(companyId, form))) {
            throw new ConflictException(ErrorCode.CALL_NUMBER_ON_DO_NOT_CALL_LIST, number);
        }
    }

    /** A suspended company, one without the voice agent, or one without the money for a call, starts no call of any kind — browser ones included. */
    void requireCompanyMayCall(long companyId) {
        companyUseCase.requireFeatureEnabled(companyId, Feature.VOICE_AGENT);
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
            pendingCallRegistry.registerManualAgent(channelId, agent, companyId, number);
        } else {
            pendingCallRegistry.registerManualScenario(channelId, scenarioId, companyId, number);
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
        pendingCallRegistry.registerManualDefinition(channelId, definition, companyId, number);
        audit.record(companyId, "CALL_ORIGINATE_TEST", "call", channelId, number);
        return new CallOriginateResponse(number, channelId);
    }

    /** See {@link OutboundRouting#requirePlatformTrunkAllowed}. */
    void requirePlatformTrunkAllowed(long companyId) {
        outboundRouting.requirePlatformTrunkAllowed(companyId);
    }

    /** The same for an outside number, over the company's trunk and with its caller id. */
    String originateExternalToStasis(long companyId, String rawNumber, String appArgs, int timeoutSec) {
        String number = PhoneNumbers.require(rawNumber);
        if (!outboundRouting.isLocalNumber(number)) {
            requireDialAllowed(companyId, rawNumber, number);
        }
        SipTrunkRow trunk = outboundRouting.isLocalNumber(number) ? null : outboundRouting.findTrunk(companyId, null);
        String endpoint = outboundRouting.findEndpoint(number, trunk);
        String dialNumber = !outboundRouting.isLocalNumber(number) && number.startsWith("+")
                ? number.substring(1) : number;
        outboundRouting.awaitTrunkSlot(number, endpoint, trunk);
        return ariClient.originateToStasis("PJSIP/" + dialNumber + "@" + endpoint, appArgs, timeoutSec,
                outboundRouting.findCallerId(trunk, null));
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
