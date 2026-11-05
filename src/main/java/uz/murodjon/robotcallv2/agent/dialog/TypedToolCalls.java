package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A stage move the model typed into its reply instead of calling the tool — a weak model
 * does this: call 12 wrote {@code <transitionTo nextStage"REASON_INQUIRY"/>}, call 13
 * {@code default_api:transitionTo{nextStage:DEBT_NOTICE}}. The text is refused as speech;
 * without this the move was lost as well, and the call stayed in a stage it had finished.
 *
 * <p>Only {@code transitionTo}: its single argument is a stage id that {@link DialogTools}
 * checks against the scenario anyway. A tool whose arguments are data is never guessed from text.
 */
final class TypedToolCalls {

    private static final Logger log = LoggerFactory.getLogger(TypedToolCalls.class);

    static final String TRANSITION_TOOL = "transitionTo";

    /** The tool name, then within a few separators (and an optional {@code nextStage}) a stage id. */
    private static final Pattern TYPED_TRANSITION = Pattern.compile(
            "transitionTo\\W{0,20}?(?:nextStage\\W{0,5})?([A-Z][A-Z0-9_]{1,63})");

    private TypedToolCalls() {
    }

    /** The stage id a typed-out {@code transitionTo} in {@code text} names, or null. */
    static String findTypedTransition(String text) {
        if (text == null || !text.contains(TRANSITION_TOOL)) {
            return null;
        }
        Matcher matcher = TYPED_TRANSITION.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Adds the {@code transitionTo} typed into {@code text} to {@code calls}, unless the model
     * also called it properly. The tool runner then treats it like any other call.
     */
    static void addTypedTransition(String channelId, String text, List<AssistantMessage.ToolCall> calls) {
        if (calls.stream().anyMatch(call -> TRANSITION_TOOL.equals(call.name()))) {
            return;
        }
        String stage = findTypedTransition(text);
        if (stage == null) {
            return;
        }
        log.warn("[{}] model typed transitionTo {} into its reply instead of calling it — running it", channelId, stage);
        calls.add(new AssistantMessage.ToolCall("typed-" + TRANSITION_TOOL, "function", TRANSITION_TOOL,
                "{\"nextStage\":\"" + stage + "\"}"));
    }
}
