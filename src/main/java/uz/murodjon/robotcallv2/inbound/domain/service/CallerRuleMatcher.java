package uz.murodjon.robotcallv2.inbound.domain.service;

import uz.murodjon.robotcallv2.inbound.domain.entity.InboundCallerRule;

import java.util.List;

/**
 * Which caller rule applies to a number: the one with the longest matching prefix, so a
 * rule for one number wins over a rule for its whole operator code. Digits are compared
 * without the leading '+', since carriers send the same number both ways.
 */
public final class CallerRuleMatcher {

    private CallerRuleMatcher() {
    }

    public static InboundCallerRule findMatch(List<InboundCallerRule> rules, String callerNumber) {
        String caller = digits(callerNumber);
        InboundCallerRule best = null;
        int bestLength = -1;
        for (InboundCallerRule rule : rules) {
            if (InboundCallerRule.ANONYMOUS.equals(rule.phonePrefix())) {
                if (caller.isEmpty() && bestLength < 0) {
                    best = rule;
                    bestLength = 0;
                }
                continue;
            }
            String prefix = digits(rule.phonePrefix());
            if (!prefix.isEmpty() && caller.startsWith(prefix) && prefix.length() > bestLength) {
                best = rule;
                bestLength = prefix.length();
            }
        }
        return best;
    }

    public static String digits(String number) {
        return number == null ? "" : number.replaceAll("\\D", "");
    }
}
