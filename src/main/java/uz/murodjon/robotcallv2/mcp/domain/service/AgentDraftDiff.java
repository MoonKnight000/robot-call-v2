package uz.murodjon.robotcallv2.mcp.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Set;
import java.util.TreeSet;

/**
 * What a publish would change: the agent's draft against its published version, as
 * {@code {field: {"published": ..., "draft": ...}}} — one level deep inside an object field
 * (script, voice, speechEngine, ...), so a changed prompt is not lost in a whole settings block.
 * Fields that only describe the row (ids, timestamps, version numbers) are not compared.
 *
 * <p>Spring-free.
 */
public final class AgentDraftDiff {

    private static final Set<String> IGNORED = Set.of("id", "companyId", "createdAt", "updatedAt", "createdBy",
            "createdByName", "publishedVersion", "publishedAt", "version", "draftVersion", "hasUnpublishedChanges");

    private AgentDraftDiff() {
    }

    public static ObjectNode diff(JsonNode published, JsonNode draft) {
        ObjectNode changes = JsonNodeFactory.instance.objectNode();
        Set<String> fields = new TreeSet<>();
        published.fieldNames().forEachRemaining(fields::add);
        draft.fieldNames().forEachRemaining(fields::add);
        for (String field : fields) {
            if (IGNORED.contains(field)) {
                continue;
            }
            JsonNode before = published.path(field);
            JsonNode after = draft.path(field);
            if (before.isObject() && after.isObject()) {
                ObjectNode inner = diff(before, after);
                if (!inner.isEmpty()) {
                    changes.set(field, inner);
                }
            } else if (!before.equals(after)) {
                ObjectNode change = changes.putObject(field);
                change.set("published", before.isMissingNode() ? NullNode.instance : before);
                change.set("draft", after.isMissingNode() ? NullNode.instance : after);
            }
        }
        return changes;
    }
}
