package uz.murodjon.robotcallv2.agent.flow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.callflow.application.port.input.CallFlowUseCase;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlowGraph;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;
import uz.murodjon.robotcallv2.inbound.application.port.input.InboundCallerRuleUseCase;
import uz.murodjon.robotcallv2.inbound.application.port.input.InboundRouteUseCase;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundCallerRule;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;
import uz.murodjon.robotcallv2.inbound.domain.enums.CallerRuleAction;
import uz.murodjon.robotcallv2.inbound.domain.enums.InboundAfterHoursAction;
import uz.murodjon.robotcallv2.schedule.application.port.input.BusinessScheduleUseCase;
import uz.murodjon.robotcallv2.schedule.domain.entity.BusinessHoursVerdict;

import java.time.Clock;
import java.time.Instant;

/**
 * Decides what an inbound call gets, before it is answered (docs/INBOUND-PBX-PLAN.md §3.3,
 * §5, §9): which route owns the number, whether a caller rule refuses the caller, and
 * then either the route's call flow or — for a route without one — its AI agent within
 * its hours.
 *
 * <p>The hours are the route's schedule if it names one, else its own daily window, read
 * in the company's time zone and with the company's holidays: the old check had no
 * weekday, no zone and called every overnight window closed.
 */
@Component
public class InboundCallGate {

    private static final Logger log = LoggerFactory.getLogger(InboundCallGate.class);
    private static final String FALLBACK_LANGUAGE = "uz-UZ";

    private final InboundRouteUseCase inboundRouteUseCase;
    private final InboundCallerRuleUseCase callerRuleUseCase;
    private final CallFlowUseCase callFlowUseCase;
    private final BusinessScheduleUseCase businessScheduleUseCase;
    private final CompanyConfigUseCase companyConfigService;
    private final CompanyUseCase companyUseCase;
    private final CallBillingUseCase callBillingUseCase;
    private final Clock clock;

    public InboundCallGate(InboundRouteUseCase inboundRouteUseCase, InboundCallerRuleUseCase callerRuleUseCase,
                           CallFlowUseCase callFlowUseCase, BusinessScheduleUseCase businessScheduleUseCase,
                           CompanyConfigUseCase companyConfigService, CompanyUseCase companyUseCase,
                           CallBillingUseCase callBillingUseCase, Clock clock) {
        this.inboundRouteUseCase = inboundRouteUseCase;
        this.callerRuleUseCase = callerRuleUseCase;
        this.callFlowUseCase = callFlowUseCase;
        this.businessScheduleUseCase = businessScheduleUseCase;
        this.companyConfigService = companyConfigService;
        this.companyUseCase = companyUseCase;
        this.callBillingUseCase = callBillingUseCase;
        this.clock = clock;
    }

    public InboundDecision decide(String did, String callerNumber) {
        InboundRoute route = did != null ? inboundRouteUseCase.resolveByDid(did) : null;
        if (route == null) {
            return InboundDecision.noRoute();
        }
        long companyId = route.companyId();
        String language = companyLanguage(companyId);

        Company company = companyUseCase.findById(companyId);
        if (company != null && company.status() == CompanyStatus.SUSPENDED) {
            log.info("Inbound call to DID {} rejected: company {} is suspended", did, companyId);
            return InboundDecision.closed(route, PbxPhrases.companyUnavailable(language), language, false, false);
        }
        if (!callBillingUseCase.hasBalanceForCall(companyId)) {
            log.info("Inbound call to DID {} rejected: company {} has insufficient balance", did, companyId);
            return InboundDecision.closed(route, PbxPhrases.companyUnavailable(language), language, false, false);
        }
        InboundCallerRule rule = findRule(companyId, callerNumber);
        if (rule != null && rule.action().blocks()) {
            return InboundDecision.reject(route, rule, language);
        }
        boolean vip = rule != null && rule.action() == CallerRuleAction.VIP;
        if (route.callFlowId() != null) {
            CallFlowGraph graph = callFlowUseCase.findPublishedGraph(companyId, route.callFlowId());
            if (graph != null) {
                return InboundDecision.flow(route, graph, language, vip);
            }
            log.warn("Route {} names call flow {}, which has nothing published — answering with its agent",
                    route.id(), route.callFlowId());
        }
        Instant now = clock.instant();
        BusinessHoursVerdict verdict = route.scheduleId() != null
                ? businessScheduleUseCase.evaluate(companyId, route.scheduleId(), now)
                : businessScheduleUseCase.evaluateDailyWindow(companyId, route.businessHoursStart(),
                route.businessHoursEnd(), now);

        if (!verdict.open()) {
            if (route.afterHoursAction() == InboundAfterHoursAction.AI_AGENT) {
                Long nightAgentId = parseLongOrNull(route.afterHoursDestination());
                Long effectiveAgentId = nightAgentId != null ? nightAgentId : route.aiAgentId();
                if (effectiveAgentId != null) {
                    log.info("Inbound call to DID {} outside business hours routed to after-hours AI agent {}", did, effectiveAgentId);
                    return InboundDecision.agent(route.withAiAgentId(effectiveAgentId), language, vip);
                }
            }

            String message;
            if (route.fallbackMessage() != null && !route.fallbackMessage().isBlank()) {
                message = route.fallbackMessage();
            } else {
                String afterHoursNotice = PbxPhrases.afterHours(language,
                        now.atZone(businessScheduleUseCase.findZone(companyId, route.scheduleId())),
                        verdict.nextOpeningAt(), verdict.holidayName());
                if (route.afterHoursAction() == InboundAfterHoursAction.VOICEMAIL) {
                    message = afterHoursNotice + " " + PbxPhrases.voicemailGreeting(language);
                } else {
                    message = afterHoursNotice;
                }
            }
            return InboundDecision.closed(route, message, language, vip, true);
        }

        if (route.aiAgentId() == null) {
            String message = route.fallbackMessage() != null && !route.fallbackMessage().isBlank()
                    ? route.fallbackMessage() : null;
            return InboundDecision.closed(route, message, language, vip, false);
        }

        return InboundDecision.agent(route, language, vip);
    }

    private static Long parseLongOrNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private InboundCallerRule findRule(long companyId, String callerNumber) {
        try {
            return callerRuleUseCase.findMatchingRule(companyId, callerNumber);
        } catch (Exception e) {
            // A rules lookup that fails must not cost the caller the call.
            log.warn("Caller rules of company {} could not be read: {}", companyId, e.getMessage());
            return null;
        }
    }

    private String companyLanguage(long companyId) {
        try {
            return companyConfigService.resolveLanguage(companyId, null);
        } catch (Exception e) {
            return FALLBACK_LANGUAGE;
        }
    }
}
