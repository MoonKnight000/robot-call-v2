package uz.murodjon.robotcallv2.agent.dialog;

import java.util.HashMap;
import java.util.Map;

/**
 * The variables a tool body can read from the call — shared by both pipelines so a company's
 * HTTP tool resolves the same placeholders whether the call runs on cascade or realtime.
 */
final class ToolCallVariables {

    /** Prefix of the raw answer a tool leaves in the call outcome; never a variable. */
    static final String TOOL_RESULT_PREFIX = "tool_";

    private ToolCallVariables() {
    }

    static Map<String, Object> build(String channelId, long companyId, String language, long callAttemptId,
                                     CallContext context) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("channel_id", channelId);
        vars.put("company_id", companyId);
        vars.put("language", language);
        vars.put("call_attempt_id", callAttemptId);
        if (context != null && context.facts() != null) {
            vars.putAll(context.facts());
        }
        // The number on the line, for a tool body's "x-source: variable" phone — an inbound
        // call's facts do not carry it (UNIVERSAL-OPERATOR-PLAN C.2, create_order).
        String phone = context != null ? context.clientPhone() : null;
        if (phone != null) {
            vars.putIfAbsent("phone", phone);
        }
        return vars;
    }

    /**
     * {@code callVariables} plus the call's recorded outcome — the fields earlier tools mapped out of
     * their answers ({@code responseMapping}) — so a later tool's template can use them as
     * {@code {{field}}}. A name the call facts or the call itself already define keeps its value,
     * and the raw {@code tool_<name>} answers stay out.
     */
    static Map<String, Object> withOutcome(Map<String, Object> callVariables, Map<String, Object> outcome) {
        if (outcome == null || outcome.isEmpty()) {
            return callVariables;
        }
        Map<String, Object> vars = new HashMap<>(callVariables);
        outcome.forEach((name, value) -> {
            if (!name.startsWith(TOOL_RESULT_PREFIX)) {
                vars.putIfAbsent(name, value);
            }
        });
        return vars;
    }
}
