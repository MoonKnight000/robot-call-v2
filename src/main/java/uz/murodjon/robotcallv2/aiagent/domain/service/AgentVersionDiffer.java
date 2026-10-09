package uz.murodjon.robotcallv2.aiagent.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentVersionChange;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** What changed between two states of an agent, setting by setting. */
public final class AgentVersionDiffer {

    /** Bookkeeping, not configuration: they differ between any two versions and change no call. */
    private static final Set<String> IGNORED = Set.of("enabled", "createdAt", "createdBy");

    private AgentVersionDiffer() {
    }

    /** The settings that differ, in path order. Objects are walked into; a list is one value. */
    public static List<AgentVersionChange> diff(AiAgent before, AiAgent after) {
        List<AgentVersionChange> changes = new ArrayList<>();
        walk("", AiAgentSnapshot.toTree(before), AiAgentSnapshot.toTree(after), changes);
        return changes;
    }

    private static void walk(String path, JsonNode before, JsonNode after, List<AgentVersionChange> changes) {
        if (isObject(before) && isObject(after)) {
            Set<String> fields = new TreeSet<>();
            before.fieldNames().forEachRemaining(fields::add);
            after.fieldNames().forEachRemaining(fields::add);
            for (String field : fields) {
                if (path.isEmpty() && IGNORED.contains(field)) {
                    continue;
                }
                walk(path.isEmpty() ? field : path + "." + field, before.get(field), after.get(field), changes);
            }
            return;
        }
        JsonNode from = isAbsent(before) ? null : before;
        JsonNode to = isAbsent(after) ? null : after;
        if (from == null ? to != null : !from.equals(to)) {
            changes.add(new AgentVersionChange(path, from, to));
        }
    }

    private static boolean isObject(JsonNode node) {
        return node != null && node.isObject();
    }

    private static boolean isAbsent(JsonNode node) {
        return node == null || node.isNull();
    }
}
