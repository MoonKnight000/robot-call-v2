package uz.murodjon.robotcallv2.agent.dialog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The caller's yes before a write tool is sent (UNIVERSAL-OPERATOR-PLAN B.3,
 * {@link HttpToolExecutor}). Held in code rather than asked of the prompt: a stranger's
 * voice writing into the company's system is exactly what a prompt cannot be trusted to hold.
 */
@Component
final class ToolConfirmation {

    private static final Logger log = LoggerFactory.getLogger(ToolConfirmation.class);
    private final ObjectMapper objectMapper;

    /**
     * Opens the answer to a write tool's first call: nothing was sent, the caller is to
     * confirm first. {@code TurnRunner} carries a tool note holding it into the next turn.
     */
    static final String CONFIRMATION_REQUIRED = "CONFIRMATION REQUIRED";

    /** Added to the description of a tool whose calls are held for the caller's yes. */
    static final String CONFIRMATION_HINT = " Call this tool as soon as the details are complete: "
            + "the first call does not send the request, it reads it back to the client for confirmation. Do not ask for confirmation separately yourself.";

    ToolConfirmation(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Whether this call of a write tool is the caller's yes to the one read back to them:
     * the same arguments, read back before the caller last spoke, answered with a plain yes.
     * Anything else — changed arguments ("3 ta emas, 2 ta"), a second call in the same
     * breath, an answer that is not a yes — is read back again instead of sent.
     */
    boolean isConfirmed(Tool tool, DialogOutcomeSink session, String canonicalArgs) {
        PendingToolConfirmation pending = session.findPendingConfirmation(tool.name());
        return pending != null
                && pending.arguments().equals(canonicalArgs)
                && session.countCallerUtterances() > pending.callerUtterances()
                && Affirmations.saidByCaller(session.findLastCallerText());
    }

    /**
     * A write tool's call that is not sent yet. The arguments are held, the readback rides
     * as the line to speak when the turn has none of its own, and the model is told that
     * nothing went out and what the caller has to say.
     */
    String holdForConfirmation(Tool tool, DialogOutcomeSink session, Map<String, Object> args,
                                      String canonicalArgs) {
        session.setPendingConfirmation(tool.name(),
                new PendingToolConfirmation(canonicalArgs, session.countCallerUtterances()));
        String readback = renderReadback(tool, args);
        if (readback != null) {
            session.addToolReply(readback);
        }
        log.info("[{}] Tool '{}' held for the caller's confirmation", session.channelId(), tool.name());
        ObjectNode node = objectMapper.createObjectNode();
        node.put("status", "CONFIRMATION_REQUIRED");
        node.put("note", CONFIRMATION_REQUIRED + ": the request has NOT been sent yet. "
                + (readback != null ? "Read this to the client: \"" + readback + "\"" : "Briefly read the details to be sent to the client")
                + " and ask them to confirm. If the client clearly says \"ha\" — call this tool again with EXACTLY "
                + "the same arguments. If the client changes something — call it with the new arguments, "
                + "which are confirmed again too.");
        if (readback == null && args != null) {
            node.set("args", objectMapper.valueToTree(args));
        }
        return node.toString();
    }

    /** The tool's {@code readbackTemplate} filled with this call's arguments, or null when it has none. */
    private static String renderReadback(Tool tool, Map<String, Object> args) {
        String template = tool.readbackTemplate();
        if (template == null || template.isBlank()) {
            return null;
        }
        Map<String, Object> values = new HashMap<>();
        if (args != null) {
            args.forEach((name, value) -> values.put(name, toSpokenText(value)));
        }
        String rendered = PromptTemplateEngine.render(template, values);
        return rendered == null || rendered.isBlank() ? null : rendered.trim();
    }

    /** An argument as it is read out: a list item by item, an object by its values — "A1 2, B7 1". */
    private static String toSpokenText(Object value) {
        if (value instanceof Map<?, ?> fields) {
            return fields.values().stream().map(ToolConfirmation::toSpokenText).collect(Collectors.joining(" "));
        }
        if (value instanceof Collection<?> items) {
            return items.stream().map(ToolConfirmation::toSpokenText).collect(Collectors.joining(", "));
        }
        return value == null ? "" : value.toString();
    }
}
