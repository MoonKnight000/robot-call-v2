package uz.murodjon.uysotvoice.agent.dialog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import uz.murodjon.uysotvoice.scenario.dto.ToolDef;
import uz.murodjon.uysotvoice.scenario.dto.ToolParamDef;

import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Turns a scenario-declared {@link ToolDef} into a callable {@link ToolCallback}
 * (ROADMAP A.3) — the generic counterpart to {@link DialogTools}' two hardcoded
 * methods (which stay hardcoded because they carry real code guardrails: rejecting a
 * past promised date). Every other scenario tool (e.g. {@code recordInterest},
 * {@code recordAnswer}) is built here at runtime: its {@code params} become a JSON
 * Schema {@link FunctionToolCallback#builder} understands, and a call simply coerces
 * each argument to its declared type and records it on the session's outcome map —
 * there is no Java method to write per scenario.
 */
final class ScenarioToolCallbackFactory {

    private static final Logger log = LoggerFactory.getLogger(ScenarioToolCallbackFactory.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ScenarioToolCallbackFactory() {
    }

    static ToolCallback build(ToolDef toolDef, DialogOutcomeSink session) {
        return FunctionToolCallback
                .<Map<String, Object>, String>builder(toolDef.name(), args -> execute(toolDef, session, args))
                .description(toolDef.description())
                .inputSchema(schemaFor(toolDef))
                .inputType(Map.class)
                .build();
    }

    private static String execute(ToolDef toolDef, DialogOutcomeSink session, Map<String, Object> args) {
        Object reply = args == null ? null : args.get(DialogTools.REPLY_PARAM);
        session.addToolReply(reply == null ? null : reply.toString());
        List<ToolParamDef> params = toolDef.params();
        if (params != null) {
            for (ToolParamDef p : params) {
                Object raw = args == null ? null : args.get(p.name());
                if (p.required() && (raw == null || raw.toString().isBlank())) {
                    return "XATO: " + p.name() + " majburiy, lekin berilmadi";
                }
                if (raw == null) {
                    continue;
                }
                Object coerced = coerce(p.type(), raw);
                if (coerced == null) {
                    return "XATO: " + p.name() + " qiymatini tushunib bo'lmadi: " + raw;
                }
                session.recordOutcome(p.name(), coerced);
            }
        }
        log.info("[{}] scenario tool {}({}) recorded", session.channelId(), toolDef.name(), args);
        return "Yozib olindi.";
    }

    /** {@code null} means the raw value could not be read as the declared type. */
    private static Object coerce(String type, Object raw) {
        try {
            return switch (type) {
                case "number" -> raw instanceof Number n ? n : new BigDecimal(raw.toString().trim());
                case "date" -> raw instanceof LocalDate d ? d : LocalDate.parse(raw.toString().trim());
                case "boolean" -> raw instanceof Boolean b ? b : Boolean.parseBoolean(raw.toString().trim());
                default -> raw.toString();
            };
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String schemaFor(ToolDef toolDef) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");
        // The line to speak, on every tool a scenario declares just as on the hardcoded
        // ones — a tool-only turn must never leave the caller waiting for a second LLM
        // round trip (see DialogTools).
        properties.putObject(DialogTools.REPLY_PARAM)
                .put("type", "string")
                .put("description", DialogTools.REPLY_DESCRIPTION);
        required.add(DialogTools.REPLY_PARAM);
        List<ToolParamDef> params = toolDef.params();
        if (params != null) {
            for (ToolParamDef p : params) {
                ObjectNode prop = properties.putObject(p.name());
                prop.put("type", jsonSchemaType(p.type()));
                if (p.constraint() != null && !p.constraint().isBlank()) {
                    prop.put("description", p.constraint());
                }
                if (p.required()) {
                    required.add(p.name());
                }
            }
        }
        try {
            return MAPPER.writeValueAsString(schema);
        } catch (Exception e) {
            // Built entirely from validated ScenarioDefinition data above — not reachable.
            throw new UncheckedIOException(new java.io.IOException(e));
        }
    }

    /** {@code "date"} stays a plain string — its format is only ever conveyed in prose (see {@link ToolParamDef}). */
    private static String jsonSchemaType(String type) {
        return switch (type) {
            case "number" -> "number";
            case "boolean" -> "boolean";
            default -> "string";
        };
    }
}
