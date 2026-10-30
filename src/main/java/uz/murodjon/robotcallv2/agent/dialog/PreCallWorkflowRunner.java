package uz.murodjon.robotcallv2.agent.dialog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.WorkflowFunction;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmSettingsUseCase;
import uz.murodjon.robotcallv2.integration.application.port.input.IntegrationToolUseCase;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolUseCase;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolEffect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs an agent's pre-call workflow slot: the functions that bring in what the conversation should
 * start from (the client's lead, contract, balance) before the agent says a word.
 *
 * <p>The functions run in layers: those waiting for nothing start together, and each next layer
 * starts when the one before it has settled. A function that fails, is turned off or never ran
 * takes down everything that waits for it, but not the rest — and nothing here throws, because a
 * call must go on without the lookups rather than not start. The whole slot shares one time
 * budget, so a slow CRM costs the caller a bounded silence.
 *
 * <p>Only {@code READ} tools run here: nobody has said anything yet, so there is no caller to
 * read a write back to and nothing to ask a yes of.
 */
@Component
public class PreCallWorkflowRunner {

    private static final Logger log = LoggerFactory.getLogger(PreCallWorkflowRunner.class);

    /** How long the whole slot may keep the caller waiting. */
    static final long BUDGET_MILLIS = 10_000;

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*}}");

    private final HttpToolExecutor httpToolExecutor;
    private final ToolUseCase toolUseCase;
    private final IntegrationToolUseCase integrationToolUseCase;
    private final CrmSettingsUseCase crmSettingsUseCase;
    private final ObjectMapper objectMapper;

    PreCallWorkflowRunner(HttpToolExecutor httpToolExecutor, ToolUseCase toolUseCase,
                          IntegrationToolUseCase integrationToolUseCase, CrmSettingsUseCase crmSettingsUseCase,
                          ObjectMapper objectMapper) {
        this.httpToolExecutor = httpToolExecutor;
        this.toolUseCase = toolUseCase;
        this.integrationToolUseCase = integrationToolUseCase;
        this.crmSettingsUseCase = crmSettingsUseCase;
        this.objectMapper = objectMapper;
    }

    /**
     * The variables the agent's pre-call functions produced; empty when it has none or none worked.
     * The functions read the same variables a tool does during the call: the call's ids, language,
     * {@code phone} and its facts.
     */
    public Map<String, Object> run(AiAgent agent, long companyId, String channelId, long callAttemptId,
                                   String language, String phone, Map<String, Object> facts) {
        if (agent == null || agent.preCallFunctions().isEmpty()) {
            return Map.of();
        }
        try {
            Map<String, Object> callVariables = new HashMap<>();
            callVariables.put("channel_id", channelId);
            callVariables.put("company_id", companyId);
            callVariables.put("language", language);
            callVariables.put("call_attempt_id", callAttemptId);
            if (facts != null) {
                callVariables.putAll(facts);
            }
            if (phone != null) {
                callVariables.putIfAbsent("phone", phone);
            }
            Map<String, Tool> tools = findTools(companyId, agent.id());
            Map<String, Object> variables = new HashMap<>(IntegrationHttpToolFactory.withCrmVariables(
                    callVariables, crmSettingsUseCase.findByCompanyId(companyId)));
            Map<String, Object> produced = new HashMap<>();
            runLayers(agent.preCallFunctions(), tools, variables, produced);
            return produced;
        } catch (RuntimeException e) {
            log.warn("Pre-call functions of agent {} not run: {}", agent.id(), e.getMessage());
            return Map.of();
        }
    }

    private Map<String, Tool> findTools(long companyId, long agentId) {
        Map<String, Tool> byName = new HashMap<>();
        try {
            integrationToolUseCase.findEnabledHttpTools(companyId).forEach(tool -> byName.put(tool.name(), tool));
        } catch (RuntimeException e) {
            log.warn("Integration tools not loaded for the pre-call functions: {}", e.getMessage());
        }
        // The agent's own tools win over an integration tool of the same name.
        toolUseCase.findAgentToolDefinitions(companyId, agentId).forEach(tool -> byName.put(tool.name(), tool));
        return byName;
    }

    private void runLayers(List<WorkflowFunction> functions, Map<String, Tool> tools,
                           Map<String, Object> variables, Map<String, Object> produced) {
        long deadline = System.currentTimeMillis() + BUDGET_MILLIS;
        Set<String> settled = new HashSet<>();
        Set<String> succeeded = new HashSet<>();
        List<WorkflowFunction> pending = new ArrayList<>();
        for (WorkflowFunction function : functions) {
            if (function.enabled()) {
                pending.add(function);
            } else {
                settled.add(function.id());
            }
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            while (!pending.isEmpty()) {
                List<WorkflowFunction> ready = new ArrayList<>();
                for (WorkflowFunction function : List.copyOf(pending)) {
                    if (!settled.containsAll(function.dependsOn())) {
                        continue;
                    }
                    pending.remove(function);
                    if (succeeded.containsAll(function.dependsOn())) {
                        ready.add(function);
                    } else {
                        settled.add(function.id());
                    }
                }
                if (ready.isEmpty() && pending.stream().noneMatch(f -> settled.containsAll(f.dependsOn()))) {
                    break;
                }
                // Not Map.copyOf: a call's facts may hold a null.
                Map<String, Object> snapshot = new HashMap<>(variables);
                List<Callable<Optional<Map<String, Object>>>> jobs = ready.stream()
                        .<Callable<Optional<Map<String, Object>>>>map(f -> () -> runFunction(f, tools, snapshot))
                        .toList();
                List<Future<Optional<Map<String, Object>>>> results = invokeAll(executor, jobs, deadline);
                for (int i = 0; i < ready.size(); i++) {
                    WorkflowFunction function = ready.get(i);
                    settled.add(function.id());
                    Optional<Map<String, Object>> outputs = outputsOf(results.get(i));
                    if (outputs.isPresent()) {
                        succeeded.add(function.id());
                        variables.putAll(outputs.get());
                        produced.putAll(outputs.get());
                    }
                }
            }
        }
    }

    /** Every job started; those still running when the deadline passes are cancelled and count as failed. */
    private List<Future<Optional<Map<String, Object>>>> invokeAll(
            ExecutorService executor, List<Callable<Optional<Map<String, Object>>>> jobs, long deadline) {
        long remaining = Math.max(1, deadline - System.currentTimeMillis());
        try {
            return executor.invokeAll(jobs, remaining, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while the pre-call functions ran", e);
        }
    }

    private Optional<Map<String, Object>> outputsOf(Future<Optional<Map<String, Object>>> future) {
        if (future.isCancelled()) {
            return Optional.empty();
        }
        try {
            return future.get();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** What the function produced; empty when it did not run or failed. */
    private Optional<Map<String, Object>> runFunction(WorkflowFunction function, Map<String, Tool> tools,
                                                      Map<String, Object> variables) {
        Tool tool = tools.get(function.toolName());
        if (tool == null) {
            log.warn("Pre-call function '{}' skipped: no tool named '{}'", function.id(), function.toolName());
            return Optional.empty();
        }
        if (tool.effect() != ToolEffect.READ) {
            log.warn("Pre-call function '{}' skipped: tool '{}' is not read-only", function.id(), tool.name());
            return Optional.empty();
        }
        Map<String, Object> arguments = new HashMap<>();
        for (Map.Entry<String, String> input : function.inputs().entrySet()) {
            Object value = resolve(input.getValue(), variables);
            if (value == null) {
                log.info("Pre-call function '{}' skipped: input '{}' has no value", function.id(), input.getKey());
                return Optional.empty();
            }
            arguments.put(input.getKey(), value);
        }
        try {
            ToolTestRun run = httpToolExecutor.executeForTest(tool, variables, arguments);
            if (run.rawBody() == null) {
                return Optional.empty();
            }
            return Optional.of(extract(function, run.rawBody()));
        } catch (RuntimeException e) {
            log.warn("Pre-call function '{}' failed: {}", function.id(), e.getMessage());
            return Optional.empty();
        }
    }

    private Map<String, Object> extract(WorkflowFunction function, String rawBody) {
        if (function.outputs().isEmpty()) {
            return Map.of();
        }
        JsonNode selected = httpToolExecutor.previewMapping(rawBody, function.outputs());
        Map<String, Object> outputs = new HashMap<>();
        if (selected != null) {
            selected.fields().forEachRemaining(field -> {
                if (!field.getValue().isNull() && !field.getValue().isMissingNode()) {
                    outputs.put(field.getKey(), objectMapper.convertValue(field.getValue(), Object.class));
                }
            });
        }
        return outputs;
    }

    /**
     * {@code template} with its {@code {{variable}}} filled in: the variable itself when the template
     * is nothing else (so a number stays a number), text otherwise. {@code null} when a variable
     * has no value.
     */
    static Object resolve(String template, Map<String, Object> variables) {
        if (template == null) {
            return null;
        }
        Matcher whole = VARIABLE.matcher(template.trim());
        if (whole.matches()) {
            return variables.get(whole.group(1));
        }
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder text = new StringBuilder();
        while (matcher.find()) {
            Object value = variables.get(matcher.group(1));
            if (value == null) {
                return null;
            }
            matcher.appendReplacement(text, Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(text);
        return text.toString();
    }
}
