package uz.murodjon.robotcallv2.inbound.domain.service;

import uz.murodjon.robotcallv2.inbound.domain.entity.InboundCallerRule;
import uz.murodjon.robotcallv2.inbound.domain.enums.CallerRuleAction;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.LocalTime;

public final class InboundRouteValidator {

    private InboundRouteValidator() {
    }

    /** A prefix is 1-15 digits (a leading '+' allowed) or ANONYMOUS; a spoken block needs its words. */
    public static void validateCallerRule(String phonePrefix, CallerRuleAction action, String message) {
        if (phonePrefix == null || !(InboundCallerRule.ANONYMOUS.equals(phonePrefix)
                || phonePrefix.matches("\\+?\\d{1,15}"))) {
            throw new ValidationException(ErrorCode.CALLER_RULE_PREFIX_INVALID, InboundCallerRule.ANONYMOUS);
        }
        if (action == CallerRuleAction.BLOCK_MESSAGE && (message == null || message.isBlank())) {
            throw new ValidationException(ErrorCode.CALLER_RULE_MESSAGE_REQUIRED);
        }
    }

    public static void validateBusinessHours(LocalTime start, LocalTime end) {
        if (start != null && start.equals(end)) {
            throw new ValidationException(ErrorCode.INBOUND_ROUTE_HOURS_IDENTICAL);
        }
    }
}
