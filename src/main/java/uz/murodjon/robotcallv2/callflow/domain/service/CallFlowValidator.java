package uz.murodjon.robotcallv2.callflow.domain.service;

import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlowGraph;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlowNode;
import uz.murodjon.robotcallv2.callflow.domain.enums.CallFlowExit;
import uz.murodjon.robotcallv2.callflow.domain.enums.CallFlowNodeType;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Whether a call-flow graph can run: every exit leads to a node, every node has what its
 * type needs. Pure shape — whether a referenced queue or agent belongs to the company is
 * the service's check. Problems are collected, not thrown one by one, so the editor can
 * show them all at once.
 */
public final class CallFlowValidator {

    public static final int MAX_NODES = 200;
    private static final Pattern NODE_ID = Pattern.compile("[A-Za-z0-9_-]{1,40}");
    private static final Pattern OPTION_KEY = Pattern.compile("[0-9*#]");
    private static final Pattern LANGUAGE_TAG = Pattern.compile("[a-z]{2}(-[A-Z]{2})?");
    private static final Pattern EXIT_NAME = Pattern.compile("[a-z0-9_]{1,40}");
    private static final Pattern E164 = Pattern.compile(PhoneNumbers.E164_REGEX);

    private CallFlowValidator() {
    }

    /** Throws {@code CALL_FLOW_INVALID} with every problem in its errors, if there are any. */
    public static void validate(CallFlowGraph graph) {
        List<String> problems = findProblems(graph);
        if (!problems.isEmpty()) {
            throw ValidationException.withErrors(ErrorCode.CALL_FLOW_INVALID, problems, problems.size());
        }
    }

    public static List<String> findProblems(CallFlowGraph graph) {
        List<String> problems = new ArrayList<>();
        if (graph == null || graph.nodes().isEmpty()) {
            problems.add("a flow needs at least one node");
            return problems;
        }
        if (graph.nodes().size() > MAX_NODES) {
            problems.add("a flow may have at most " + MAX_NODES + " nodes");
        }
        if (graph.node(graph.entry()) == null) {
            problems.add("entry '" + graph.entry() + "' is not a node");
        }
        for (Map.Entry<String, CallFlowNode> entry : graph.nodes().entrySet()) {
            String id = entry.getKey();
            CallFlowNode node = entry.getValue();
            if (!NODE_ID.matcher(id).matches()) {
                problems.add("node id '" + id + "' must be 1-40 letters, digits, '-' or '_'");
            }
            if (node == null || node.type() == null) {
                problems.add(id + ": type is required");
                continue;
            }
            checkExits(graph, id, node, problems);
            checkType(graph, id, node, problems);
        }
        return problems;
    }

    private static void checkExits(CallFlowGraph graph, String id, CallFlowNode node, List<String> problems) {
        for (Map.Entry<String, String> exit : node.exits().entrySet()) {
            if (!node.type().allowsExit(exit.getKey())
                    || (node.type() == CallFlowNodeType.WEBHOOK_ROUTER && !EXIT_NAME.matcher(exit.getKey()).matches())) {
                problems.add(id + ": " + node.type() + " has no exit '" + exit.getKey() + "'");
            }
            if (graph.node(exit.getValue()) == null) {
                problems.add(id + ": exit '" + exit.getKey() + "' leads to missing node '" + exit.getValue() + "'");
            }
        }
    }

    private static void checkType(CallFlowGraph graph, String id, CallFlowNode node, List<String> problems) {
        switch (node.type()) {
            case PLAY -> requireSomethingToSay(id, node, problems);
            case SCHEDULE -> {
                require(node.scheduleId() != null, id + ": scheduleId is required", problems);
                require(node.exit(CallFlowExit.OPEN) != null && node.exit(CallFlowExit.CLOSED) != null,
                        id + ": SCHEDULE needs both an 'open' and a 'closed' exit", problems);
            }
            case MENU -> {
                requireSomethingToSay(id, node, problems);
                require(!node.options().isEmpty(), id + ": a menu needs at least one option", problems);
                for (Map.Entry<String, String> option : node.options().entrySet()) {
                    require(OPTION_KEY.matcher(option.getKey()).matches(),
                            id + ": option key '" + option.getKey() + "' must be one of 0-9, * or #", problems);
                    require(graph.node(option.getValue()) != null,
                            id + ": option " + option.getKey() + " leads to missing node '" + option.getValue() + "'",
                            problems);
                }
                for (String key : node.keywords().keySet()) {
                    require(node.options().containsKey(key),
                            id + ": keywords are given for '" + key + "', which is not an option", problems);
                }
                requireRange(node.timeoutSec(), 1, 30, id + ": timeoutSec", problems);
                requireRange(node.maxRetries(), 0, 5, id + ": maxRetries", problems);
                requireRange(node.extensionDigits(), 2, 6, id + ": extensionDigits", problems);
            }
            case LANGUAGE_SELECT -> {
                requireSomethingToSay(id, node, problems);
                require(!node.options().isEmpty(), id + ": language selection needs at least one option", problems);
                for (Map.Entry<String, String> option : node.options().entrySet()) {
                    require(OPTION_KEY.matcher(option.getKey()).matches(),
                            id + ": option key '" + option.getKey() + "' must be one of 0-9, * or #", problems);
                    require(option.getValue() != null && LANGUAGE_TAG.matcher(option.getValue()).matches(),
                            id + ": option " + option.getKey() + " must name a language like uz-UZ", problems);
                }
                require(node.language() == null || LANGUAGE_TAG.matcher(node.language()).matches(),
                        id + ": language must be a tag like uz-UZ", problems);
                requireRange(node.timeoutSec(), 1, 30, id + ": timeoutSec", problems);
            }
            case AI_AGENT -> require(node.aiAgentId() != null, id + ": aiAgentId is required", problems);
            case QUEUE -> require(node.queueId() != null, id + ": queueId is required", problems);
            case RING_GROUP -> {
                require(!node.userIds().isEmpty(), id + ": userIds must name at least one operator", problems);
                requireRange(node.timeoutSec(), 5, 120, id + ": timeoutSec", problems);
            }
            case EXTENSION -> {
                require(node.userIds().size() == 1, id + ": EXTENSION rings exactly one operator (userIds)", problems);
                requireRange(node.timeoutSec(), 5, 120, id + ": timeoutSec", problems);
            }
            case FORWARD_EXTERNAL -> {
                require(node.phoneNumber() != null && E164.matcher(node.phoneNumber()).matches(),
                        id + ": phoneNumber must be a phone number", problems);
                requireRange(node.timeoutSec(), 5, 120, id + ": timeoutSec", problems);
            }
            case VOICEMAIL -> require(node.voicemailBoxId() != null, id + ": voicemailBoxId is required", problems);
            case WEBHOOK_ROUTER -> require(node.url() != null
                            && (node.url().startsWith("https://") || node.url().startsWith("http://")),
                    id + ": url must be an http(s) address", problems);
            case HANGUP -> require(node.exits().isEmpty(), id + ": HANGUP has no exits", problems);
            case CALLBACK_OFFER, CALLER_ROUTER -> {
                // nothing required: both have a spoken default and exits only
            }
        }
    }

    private static void requireSomethingToSay(String id, CallFlowNode node, List<String> problems) {
        boolean hasText = node.prompt().values().stream().anyMatch(text -> text != null && !text.isBlank());
        require(hasText || node.audioFileId() != null, id + ": a prompt or an audioFileId is required", problems);
    }

    private static void requireRange(Integer value, int min, int max, String field, List<String> problems) {
        if (value != null && (value < min || value > max)) {
            problems.add(field + " must be between " + min + " and " + max);
        }
    }

    private static void require(boolean condition, String problem, List<String> problems) {
        if (!condition) {
            problems.add(problem);
        }
    }
}
