package uz.murodjon.robotcallv2.agent.ari;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.infrastructure.config.CompanyProperties;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.siptrunk.application.dto.SipTrunkRow;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.CallerIdPoolUseCase;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.SipTrunkUseCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What an outbound call goes out on: the trunks to try, the PJSIP endpoint and the caller id.
 * Shared by the engines' {@code originate} and by the PBX side's outside calls, so every
 * outbound call picks its trunk by the same rules.
 */
@Component
class OutboundRouting {

    private static final Logger log = LoggerFactory.getLogger(OutboundRouting.class);

    private final AsteriskProperties asteriskProperties;
    private final SipTrunkUseCase sipTrunkUseCase;
    /** Whose calls may go out over the platform's own trunk. */
    private final CompanyProperties companyProperties;
    private final TrunkCallPacer trunkCallPacer;
    private final ObjectProvider<CallerIdPoolUseCase> callerIdPoolUseCaseProvider;

    OutboundRouting(AsteriskProperties asteriskProperties,
                    SipTrunkUseCase sipTrunkUseCase,
                    CompanyProperties companyProperties,
                    TrunkCallPacer trunkCallPacer,
                    ObjectProvider<CallerIdPoolUseCase> callerIdPoolUseCaseProvider) {
        this.asteriskProperties = asteriskProperties;
        this.sipTrunkUseCase = sipTrunkUseCase;
        this.companyProperties = companyProperties;
        this.trunkCallPacer = trunkCallPacer;
        this.callerIdPoolUseCaseProvider = callerIdPoolUseCaseProvider;
    }

    /**
     * The trunks to try, in order: the one asked for alone, else the company's default followed
     * by its other enabled ones. A single {@code null} stands for the platform's own trunk.
     */
    List<SipTrunkRow> findCandidateTrunks(long companyId, Long sipTrunkId) {
        if (sipTrunkId != null) {
            // Used or refused: dialling it over another trunk showed the callee a caller ID nobody chose.
            return List.of(sipTrunkUseCase.requireTrunk(companyId, sipTrunkId));
        }
        SipTrunkRow defaultTrunk = sipTrunkUseCase.findDefaultForCall(companyId);
        if (defaultTrunk != null) {
            List<SipTrunkRow> allTrunks = sipTrunkUseCase.findAllEnabledForCompany(companyId);
            if (allTrunks == null || allTrunks.size() <= 1) {
                return List.of(defaultTrunk);
            }
            List<SipTrunkRow> candidates = new ArrayList<>();
            candidates.add(defaultTrunk);
            for (SipTrunkRow t : allTrunks) {
                if (t.id() != defaultTrunk.id()) {
                    candidates.add(t);
                }
            }
            return candidates;
        }
        requirePlatformTrunkAllowed(companyId);
        return Collections.singletonList(null);
    }

    /** The first of {@link #findCandidateTrunks}: where a call without failover goes. */
    SipTrunkRow findTrunk(long companyId, Long sipTrunkId) {
        List<SipTrunkRow> trunks = findCandidateTrunks(companyId, sipTrunkId);
        return trunks.isEmpty() ? null : trunks.getFirst();
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

    boolean isLocalNumber(String number) {
        String local = asteriskProperties.localEndpoint();
        String pattern = asteriskProperties.localNumberPattern();
        return local != null && !local.isBlank()
                && pattern != null && !pattern.isBlank()
                && number != null && number.matches(pattern);
    }

    String findEndpoint(String number, SipTrunkRow trunk) {
        if (isLocalNumber(number)) {
            return asteriskProperties.localEndpoint();
        }
        return trunk != null ? trunk.pjsipEndpoint() : asteriskProperties.trunkEndpoint();
    }

    /** Under the trunk's calls-per-second limit; the local softphone has none. */
    void awaitTrunkSlot(String number, String endpoint, SipTrunkRow trunk) {
        if (!isLocalNumber(number)) {
            trunkCallPacer.awaitSlot(endpoint, trunk != null ? trunk.maxCps() : null);
        }
    }

    String findCallerId(SipTrunkRow trunk, OutboundCall outboundCall, long companyId) {
        if (outboundCall != null && outboundCall.callerIdPoolId() != null) {
            CallerIdPoolUseCase poolUseCase = callerIdPoolUseCaseProvider.getIfAvailable();
            if (poolUseCase != null) {
                try {
                    return poolUseCase.acquireCallerId(companyId, outboundCall.callerIdPoolId());
                } catch (RuntimeException e) {
                    log.warn("Failed to acquire caller ID from pool {}: {}", outboundCall.callerIdPoolId(), e.getMessage());
                }
            }
        }
        if (trunk != null && trunk.callerId() != null && !trunk.callerId().isBlank()) {
            return trunk.callerId();
        }
        return asteriskProperties.callerId();
    }
}
