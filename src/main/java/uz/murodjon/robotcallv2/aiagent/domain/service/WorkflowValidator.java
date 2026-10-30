package uz.murodjon.robotcallv2.aiagent.domain.service;

import uz.murodjon.robotcallv2.aiagent.domain.entity.WorkflowFunction;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The rules a slot of workflow functions must keep: a size, a depth, names, and a graph without
 * loops. Every problem found is reported, not only the first.
 */
public final class WorkflowValidator {

    /** Functions in one slot. */
    public static final int MAX_FUNCTIONS = 15;
    /** Longest chain of functions waiting on one another in one slot. */
    public static final int MAX_DEPTH = 4;

    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    private WorkflowValidator() {
    }

    public static void validate(List<WorkflowFunction> functions) {
        List<String> errors = new ArrayList<>();
        if (functions.size() > MAX_FUNCTIONS) {
            errors.add("a slot holds at most " + MAX_FUNCTIONS + " functions, got " + functions.size());
        }
        Map<String, WorkflowFunction> byId = new HashMap<>();
        for (WorkflowFunction function : functions) {
            if (function.id() == null || !NAME.matcher(function.id()).matches()) {
                errors.add("function id '" + function.id() + "' must be letters, digits and _, starting with a letter or _");
            } else if (byId.put(function.id(), function) != null) {
                errors.add("function id '" + function.id() + "' is used twice");
            }
            function.outputs().forEach((variable, path) -> {
                if (!NAME.matcher(variable).matches()) {
                    errors.add(function.id() + ": output name '" + variable + "' must be letters, digits and _");
                }
                if (path == null || !path.trim().startsWith("$")) {
                    errors.add(function.id() + ": output '" + variable + "' must be a path starting with $, got '" + path + "'");
                }
            });
            function.inputs().keySet().stream().filter(name -> name == null || name.isBlank())
                    .findFirst().ifPresent(name -> errors.add(function.id() + ": an input has no name"));
        }
        boolean dependenciesKnown = true;
        for (WorkflowFunction function : functions) {
            for (String dependency : function.dependsOn()) {
                if (!byId.containsKey(dependency)) {
                    errors.add(function.id() + " depends on '" + dependency + "', which is not in the slot");
                    dependenciesKnown = false;
                } else if (dependency.equals(function.id())) {
                    errors.add(function.id() + " depends on itself");
                    dependenciesKnown = false;
                }
            }
        }
        if (dependenciesKnown) {
            checkGraph(functions, byId, errors);
        }
        if (!errors.isEmpty()) {
            throw ValidationException.withErrors(ErrorCode.WORKFLOW_INVALID, errors, errors.size());
        }
    }

    /** A loop, or a chain deeper than {@link #MAX_DEPTH}. */
    private static void checkGraph(List<WorkflowFunction> functions, Map<String, WorkflowFunction> byId, List<String> errors) {
        Map<String, Integer> depth = new HashMap<>();
        for (WorkflowFunction function : functions) {
            if (depthOf(function, byId, depth, new HashSet<>()) < 0) {
                errors.add("the functions depend on one another in a loop (at " + function.id() + ")");
                return;
            }
        }
        depth.entrySet().stream().filter(entry -> entry.getValue() > MAX_DEPTH).findFirst().ifPresent(entry ->
                errors.add("a chain of " + entry.getValue() + " functions ends at " + entry.getKey()
                        + "; at most " + MAX_DEPTH + " may wait on one another"));
    }

    /** 1 for a function with nothing to wait for; -1 when it is part of a loop. */
    private static int depthOf(WorkflowFunction function, Map<String, WorkflowFunction> byId,
                               Map<String, Integer> depth, Set<String> path) {
        Integer known = depth.get(function.id());
        if (known != null) {
            return known;
        }
        if (!path.add(function.id())) {
            return -1;
        }
        int deepest = 0;
        for (String dependency : function.dependsOn()) {
            int d = depthOf(byId.get(dependency), byId, depth, path);
            if (d < 0) {
                return -1;
            }
            deepest = Math.max(deepest, d);
        }
        path.remove(function.id());
        depth.put(function.id(), deepest + 1);
        return deepest + 1;
    }
}
