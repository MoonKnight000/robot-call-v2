package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.integration.application.port.input.IntegrationToolUseCase;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns the HTTP tools the platform admin defined for the integration into callbacks for one
 * call. The tools arrive already pointed at the company's own account and carrying its token
 * ({@link IntegrationToolUseCase#findEnabledHttpTools}); they have no company or id on purpose,
 * so the executor reads no company secrets, starts no OAuth of its own and writes no execution
 * row for a tool that is not a stored tool.
 */
@Component
class IntegrationHttpToolFactory {

    private static final Logger log = LoggerFactory.getLogger(IntegrationHttpToolFactory.class);

    private final HttpToolExecutor httpToolExecutor;
    private final IntegrationToolUseCase integrationToolUseCase;

    IntegrationHttpToolFactory(HttpToolExecutor httpToolExecutor, IntegrationToolUseCase integrationToolUseCase) {
        this.httpToolExecutor = httpToolExecutor;
        this.integrationToolUseCase = integrationToolUseCase;
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
            List<ToolCallback> callbacks = new ArrayList<>();
            for (Tool tool : integrationToolUseCase.findEnabledHttpTools(companyId)) {
                callbacks.add(httpToolExecutor.buildCallback(tool, session, callVariables));
            }
            return callbacks;
        } catch (RuntimeException e) {
            log.warn("Integration HTTP tools of company {} not offered: {}", companyId, e.getMessage());
            return List.of();
        }
    }
}
