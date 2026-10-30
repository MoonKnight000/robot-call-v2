package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmSettingsUseCase;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmSettings;
import uz.murodjon.robotcallv2.integration.application.port.input.IntegrationToolUseCase;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the HTTP tools the platform admin defined for the integration into callbacks for one
 * call. The tools arrive already pointed at the company's own account and carrying its token
 * ({@link IntegrationToolUseCase#findEnabledHttpTools}); they have no company or id on purpose,
 * so the executor reads no company secrets, starts no OAuth of its own and writes no execution
 * row for a tool that is not a stored tool.
 *
 * <p>Besides the call's own variables, these tools can read what only the CRM knows about the
 * call ({@link #withCrmVariables}): the phone as the CRM matches it, and the company's CRM
 * settings (which pipeline stage a new lead starts in, who the AI acts as).
 */
@Component
class IntegrationHttpToolFactory {

    private static final Logger log = LoggerFactory.getLogger(IntegrationHttpToolFactory.class);

    /** Uysot matches a client's phone on its last nine digits, digits only. */
    private static final int PHONE_SUFFIX_DIGITS = 9;

    private final HttpToolExecutor httpToolExecutor;
    private final IntegrationToolUseCase integrationToolUseCase;
    private final CrmSettingsUseCase crmSettingsUseCase;

    IntegrationHttpToolFactory(HttpToolExecutor httpToolExecutor, IntegrationToolUseCase integrationToolUseCase,
                               CrmSettingsUseCase crmSettingsUseCase) {
        this.httpToolExecutor = httpToolExecutor;
        this.integrationToolUseCase = integrationToolUseCase;
        this.crmSettingsUseCase = crmSettingsUseCase;
    }

    /** The text the platform admin set for built-in Uysot tools, by tool name; empty if none or on a failure. */
    Map<String, String> findBuiltInDescriptions() {
        try {
            return integrationToolUseCase.findBuiltInModelDescriptions();
        } catch (RuntimeException e) {
            log.warn("Built-in tool descriptions not loaded: {}", e.getMessage());
            return Map.of();
        }
    }

    /** Empty when the company is not connected to Uysot or has no such tool on. Never throws: a call goes on without them. */
    List<ToolCallback> build(DialogOutcomeSink session, long companyId, Map<String, Object> callVariables) {
        try {
            List<Tool> tools = integrationToolUseCase.findEnabledHttpTools(companyId);
            if (tools.isEmpty()) {
                return List.of();
            }
            Map<String, Object> variables = withCrmVariables(callVariables, crmSettingsUseCase.findByCompanyId(companyId));
            List<ToolCallback> callbacks = new ArrayList<>();
            for (Tool tool : tools) {
                callbacks.add(httpToolExecutor.buildCallback(tool, session, variables));
            }
            return callbacks;
        } catch (RuntimeException e) {
            log.warn("Integration HTTP tools of company {} not offered: {}", companyId, e.getMessage());
            return List.of();
        }
    }

    /**
     * The call's variables plus what these tools read from the CRM side:
     * <ul>
     *   <li>{@code phone_last9} — the last nine digits of {@code phone}, which is how Uysot's
     *       {@code search} matches a client;</li>
     *   <li>{@code crm_ai_employee_id}, {@code crm_new_lead_status_id},
     *       {@code crm_meeting_booked_status_id}, {@code crm_promise_to_pay_status_id},
     *       {@code crm_not_interested_status_id}, {@code crm_callback_status_id},
     *       {@code crm_task_type_id} — the company's CRM settings.</li>
     * </ul>
     * A value that is not known is left out, so its placeholder stays unresolved rather than
     * reading as zero.
     */
    static Map<String, Object> withCrmVariables(Map<String, Object> callVariables, CrmSettings settings) {
        Map<String, Object> variables = new HashMap<>(callVariables);
        if (variables.get("phone") instanceof String phone) {
            String digits = phone.replaceAll("\\D", "");
            if (!digits.isEmpty()) {
                variables.putIfAbsent("phone_last9",
                        digits.length() > PHONE_SUFFIX_DIGITS ? digits.substring(digits.length() - PHONE_SUFFIX_DIGITS) : digits);
            }
        }
        putIfKnown(variables, "crm_ai_employee_id", settings.aiEmployeeId());
        putIfKnown(variables, "crm_new_lead_status_id", settings.newLeadStatusId());
        putIfKnown(variables, "crm_meeting_booked_status_id", settings.meetingBookedStatusId());
        putIfKnown(variables, "crm_promise_to_pay_status_id", settings.promiseToPayStatusId());
        putIfKnown(variables, "crm_not_interested_status_id", settings.notInterestedStatusId());
        putIfKnown(variables, "crm_callback_status_id", settings.callbackStatusId());
        putIfKnown(variables, "crm_task_type_id", settings.taskTypeId());
        return variables;
    }

    private static void putIfKnown(Map<String, Object> variables, String name, Integer value) {
        if (value != null) {
            variables.put(name, value);
        }
    }
}
