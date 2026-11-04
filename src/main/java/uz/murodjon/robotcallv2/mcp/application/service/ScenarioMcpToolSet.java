package uz.murodjon.robotcallv2.mcp.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.scenario.application.dto.CloneScenarioRequest;
import uz.murodjon.robotcallv2.scenario.application.dto.CreateScenarioRequest;
import uz.murodjon.robotcallv2.scenario.application.dto.ScenarioRow;
import uz.murodjon.robotcallv2.scenario.application.dto.UpdateScenarioRequest;
import uz.murodjon.robotcallv2.scenario.application.port.input.ScenarioUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioFilter;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Scenarios — what an agent says and in which order — for the copilot and MCP clients. A save
 * never rewrites a row: it is a new version with a new id, and agents keep the id they were
 * given, so editing cannot change a live call until a person points an agent at the new version
 * and publishes it.
 */
@Component
@Order(30)
public class ScenarioMcpToolSet implements McpToolSet {

    private static final Set<Permission> SCENARIO_READ = EnumSet.of(Permission.SCENARIO_READ, Permission.SCENARIO_EDIT);
    private static final Set<Permission> SCENARIO_WRITE = EnumSet.of(Permission.SCENARIO_EDIT);

    private static final String STAGE_FIELDS_HINT = """
            id, title, purpose (what the agent does here), allowedTransitions ([stage ids]), \
            transitionConditions ({stageId: "when the caller ..."}), branches (fact-based, no LLM), staticLine \
            (fixed words said on entry), requiredOutcomes, allowedTools, emotion, model, type, fallbackLine""";

    private final ScenarioUseCase scenarioUseCase;
    private final McpToolArguments arguments;

    public ScenarioMcpToolSet(ScenarioUseCase scenarioUseCase, Validator validator) {
        this.scenarioUseCase = scenarioUseCase;
        this.arguments = new McpToolArguments(validator);
    }

    @Override
    public List<McpToolDefinition> findTools() {
        return List.of(
                new McpToolDefinition("list_scenarios", """
                        The company's scenarios and the built-in templates (builtin: true), latest version \
                        of each. Start a new scenario from a template with clone_scenario.""", SCENARIO_READ, """
                        {"type": "object", "properties": {
                          "builtinOnly": {"type": "boolean"},
                          "page": {"type": "integer", "minimum": 0},
                          "size": {"type": "integer", "minimum": 1, "maximum": 100}}}""",
                        (companyId, tool, args, draftOnly) -> scenarioUseCase.list(companyId,
                                arguments.convert(tool, args, ScenarioFilter.class))),

                new McpToolDefinition("get_scenario", """
                        One scenario in full: stages (%s), globalNodes, factSchema, outcomeSchema, tools, \
                        rolePrompt, guardrails, callPurpose, startStageId.""".formatted(STAGE_FIELDS_HINT),
                        SCENARIO_READ, """
                        {"type": "object", "required": ["scenarioId"], "properties": {"scenarioId": {"type": "integer"}}}""",
                        (companyId, tool, args, draftOnly) -> scenarioUseCase.findScenarioRow(companyId,
                                arguments.requireId(tool, args, "scenarioId"))),

                new McpToolDefinition("validate_scenario", """
                        Check a scenario definition without saving it: unknown stage ids in transitions, \
                        unreachable stages, missing start stage and the like.""", SCENARIO_READ, """
                        {"type": "object", "required": ["definition"], "properties": {"definition": {"type": "object"}}}""",
                        (companyId, tool, args, draftOnly) -> scenarioUseCase.validate(
                                arguments.convert(tool, args.get("definition"), ScenarioDefinition.class))),

                new McpToolDefinition("create_scenario", """
                        Save a new scenario (version 1). Attach it to an agent's draft with update_agent_draft \
                        (script.scenarioId).""", SCENARIO_WRITE, """
                        {"type": "object", "required": ["name", "definition"], "properties": {
                          "name": {"type": "string"},
                          "description": {"type": "string"},
                          "scenarioKey": {"type": "string", "description": "Stable key across versions; omit to derive one"},
                          "definition": {"type": "object"}}}""",
                        (companyId, tool, args, draftOnly) -> scenarioUseCase.create(companyId,
                                arguments.convert(tool, args, CreateScenarioRequest.class))),

                new McpToolDefinition("clone_scenario", """
                        Copy a scenario (a built-in template too) into a new one the company owns.""",
                        SCENARIO_WRITE, """
                        {"type": "object", "required": ["scenarioId", "name"], "properties": {
                          "scenarioId": {"type": "integer"},
                          "name": {"type": "string"}}}""",
                        (companyId, tool, args, draftOnly) -> {
                            ObjectNode body = args.deepCopy();
                            body.remove("scenarioId");
                            return scenarioUseCase.clone(companyId, arguments.requireId(tool, args, "scenarioId"),
                                    arguments.convert(tool, body, CloneScenarioRequest.class));
                        }),

                new McpToolDefinition("update_scenario", """
                        Save a scenario's next version: name, description, or top-level definition fields \
                        (rolePrompt, guardrails, callPurpose, factSchema, outcomeSchema, ...) — a given field \
                        replaces the old one, omitted ones stay. Returns the new version's row (new id).""",
                        SCENARIO_WRITE, """
                        {"type": "object", "required": ["scenarioId"], "properties": {
                          "scenarioId": {"type": "integer"},
                          "name": {"type": "string"},
                          "description": {"type": "string"},
                          "definition": {"type": "object"}}}""",
                        (companyId, tool, args, draftOnly) -> saveVersion(companyId, tool, args, (definition) -> {
                            if (args.has("definition") && args.get("definition").isObject()) {
                                args.get("definition").properties().forEach(e -> definition.set(e.getKey(), e.getValue()));
                            }
                        })),

                new McpToolDefinition("upsert_scenario_stage", """
                        Add a stage, or change one by id (given fields are merged into it), and save the \
                        scenario's next version. Stage fields: %s.""".formatted(STAGE_FIELDS_HINT),
                        SCENARIO_WRITE, """
                        {"type": "object", "required": ["scenarioId", "stage"], "properties": {
                          "scenarioId": {"type": "integer"},
                          "stage": {"type": "object", "required": ["id"]}}}""",
                        (companyId, tool, args, draftOnly) -> saveVersion(companyId, tool, args,
                                definition -> upsertByKey(definition, "stages", "id", args.get("stage")))),

                new McpToolDefinition("upsert_global_node", """
                        Add a question answered at any stage without leaving it ("siz \
                        kimsiz?", "raqamimni qayerdan oldingiz?") with its answer, or change the answer of the same question, and save the scenario's next version.""",
                        SCENARIO_WRITE, """
                        {"type": "object", "required": ["scenarioId", "node"], "properties": {
                          "scenarioId": {"type": "integer"},
                          "node": {"type": "object", "required": ["question", "answer"], "properties": {"question": {"type": "string"}, "answer": {"type": "string"}}}}}""",
                        (companyId, tool, args, draftOnly) -> saveVersion(companyId, tool, args,
                                definition -> upsertByKey(definition, "globalNodes", "question", args.get("node")))));
    }

    /** Reads the latest version, lets {@code change} edit its definition as JSON, and saves the next version. */
    private ScenarioRow saveVersion(long companyId, String tool, JsonNode args, DefinitionChange change) {
        long scenarioId = arguments.requireId(tool, args, "scenarioId");
        ScenarioRow current = scenarioUseCase.findScenarioRow(companyId, scenarioId);
        ObjectNode definition = arguments.mapper().valueToTree(current.definition());
        change.apply(definition);
        String name = arguments.optionalText(args, "name");
        String description = args.has("description") ? arguments.optionalText(args, "description") : current.description();
        ObjectNode body = arguments.mapper().createObjectNode();
        body.put("name", name != null ? name : current.name());
        body.put("description", description);
        body.set("definition", definition);
        body.put("version", current.version());
        return scenarioUseCase.update(companyId, scenarioId, arguments.convert(tool, body, UpdateScenarioRequest.class));
    }

    private void upsertByKey(ObjectNode definition, String listField, String keyField, JsonNode item) {
        JsonNode existing = definition.get(listField);
        ArrayNode list = existing instanceof ArrayNode array ? array : definition.putArray(listField);
        String key = item.path(keyField).asText();
        for (JsonNode element : list) {
            if (element instanceof ObjectNode object && key.equals(object.path(keyField).asText())) {
                arguments.merge(object, item);
                return;
            }
        }
        list.add(item);
    }

    @FunctionalInterface
    private interface DefinitionChange {
        void apply(ObjectNode definition);
    }
}
