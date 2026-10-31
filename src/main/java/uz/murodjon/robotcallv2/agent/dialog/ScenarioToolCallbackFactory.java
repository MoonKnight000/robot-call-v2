package uz.murodjon.robotcallv2.agent.dialog;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import uz.murodjon.robotcallv2.scenario.domain.entity.ToolDef;
import uz.murodjon.robotcallv2.scenario.domain.entity.ToolParamDef;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a scenario-declared {@link ToolDef} into a callable {@link ToolCallback}
 * (ROADMAP A.3). Every outcome tool a scenario has (e.g. {@code recordPaymentPromise},
 * {@code recordInterest}) is built here at runtime: its {@code params} become a JSON
 * Schema {@link FunctionToolCallback#builder} understands, and a call coerces each
 * argument to its declared type, records it on the session's outcome map and, when the
 * tool names a {@link ToolDef#disposition()}, gives the call that disposition — there is
 * no Java method to write per scenario. {@link DialogTools} keeps only the tools that
 * steer the call itself (transfer, hang up, language).
 */
final class ScenarioToolCallbackFactory {

    private static final Logger log = LoggerFactory.getLogger(ScenarioToolCallbackFactory.class);

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
        List<ToolParamDef> params = toolDef.params();
        // Every argument is checked before any is recorded: a refused date must not leave
        // the amount that came with it behind as half an outcome.
        Map<String, Object> accepted = new LinkedHashMap<>();
        if (params != null) {
            for (ToolParamDef p : params) {
                Object raw = args == null ? null : args.get(p.name());
                if (p.required() && (raw == null || raw.toString().isBlank())) {
                    return "ERROR: " + p.name() + " is required but was not given";
                }
                if (raw == null || raw.toString().isBlank()) {
                    continue;
                }
                Object coerced = coerce(p.type(), raw);
                if (coerced == null) {
                    return "ERROR: could not understand the value of " + p.name() + ": " + raw;
                }
                String refusal = checkDateRange(p, coerced);
                if (refusal != null) {
                    return refusal;
                }
                accepted.put(p.name(), coerced);
            }
        }
        // Only now: the line confirms what was just recorded, so a refused call must not speak it.
        Object reply = args == null ? null : args.get(DialogTools.REPLY_PARAM);
        session.addToolReply(reply == null ? null : reply.toString());
        accepted.forEach(session::recordOutcome);
        Disposition disposition = findDisposition(toolDef);
        if (disposition != null) {
            session.setDisposition(disposition);
        }
        log.info("[{}] scenario tool {}({}) recorded", session.channelId(), toolDef.name(), args);
        return "Yozib olindi.";
    }

    /** The disposition a tool gives the call, or null when it names none — or one this build does not know. */
    private static Disposition findDisposition(ToolDef toolDef) {
        if (toolDef.disposition() == null || toolDef.disposition().isBlank()) {
            return null;
        }
        try {
            return Disposition.valueOf(toolDef.disposition().trim());
        } catch (IllegalArgumentException e) {
            log.warn("scenario tool {} names an unknown disposition '{}'", toolDef.name(), toolDef.disposition());
            return null;
        }
    }

    /**
     * The refusal for a {@code futureDate} in the past or a {@code pastDate} in the future,
     * worded so the model can work the right date out and call again; null when the date is fine.
     */
    private static String checkDateRange(ToolParamDef param, Object value) {
        if (!(value instanceof LocalDate date)) {
            return null;
        }
        LocalDate today = LocalDate.now();
        if ("futureDate".equals(param.type()) && date.isBefore(today)) {
            return "ERROR: " + date + " is in the past. Today is " + today
                    + ". Work the date out from today and send it again, or ask the client for the exact date.";
        }
        if ("pastDate".equals(param.type()) && date.isAfter(today)) {
            return "ERROR: " + date + " is in the future. Today is " + today
                    + ". Clarify the date with the client.";
        }
        return null;
    }

    /** {@code null} means the raw value could not be read as the declared type. */
    private static Object coerce(String type, Object raw) {
        try {
            return switch (type) {
                case "number" -> raw instanceof Number n ? n : new BigDecimal(raw.toString().trim());
                case "date", "futureDate", "pastDate" ->
                        raw instanceof LocalDate d ? d : LocalDate.parse(raw.toString().trim());
                case "boolean" -> raw instanceof Boolean b ? b : Boolean.parseBoolean(raw.toString().trim());
                default -> raw.toString();
            };
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String schemaFor(ToolDef toolDef) {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");
        // The line to speak, on every tool a scenario declares just as on the hardcoded
        // ones — offered, not required: the model is asked to write the line as plain text
        // so synthesis can start before generation ends, and this stays as the fallback for
        // a tool-only turn, which must never cost a second LLM round trip (see DialogTools).
        properties.putObject(DialogTools.REPLY_PARAM)
                .put("type", "string")
                .put("description", DialogTools.REPLY_DESCRIPTION);
        List<ToolParamDef> params = toolDef.params();
        if (params != null) {
            for (ToolParamDef p : params) {
                ObjectNode prop = properties.putObject(p.name());
                prop.put("type", jsonSchemaType(p.type()));
                String description = describe(p);
                if (description != null) {
                    prop.put("description", description);
                }
                if (p.required()) {
                    required.add(p.name());
                }
            }
        }
        return schema.toString();
    }

    /** The date types stay plain strings — their format is only ever conveyed in prose (see {@link ToolParamDef}). */
    private static String jsonSchemaType(String type) {
        return switch (type) {
            case "number" -> "number";
            case "boolean" -> "boolean";
            default -> "string";
        };
    }

    /** The parameter's own constraint text, led by the date format a date type is read in. */
    private static String describe(ToolParamDef param) {
        boolean date = "date".equals(param.type()) || "futureDate".equals(param.type())
                || "pastDate".equals(param.type());
        String constraint = param.constraint() == null || param.constraint().isBlank() ? null : param.constraint();
        if (!date) {
            return constraint;
        }
        return constraint == null ? "format yyyy-MM-dd" : "format yyyy-MM-dd; " + constraint;
    }
}
