package uz.murodjon.robotcallv2.scenario.domain.entity;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import uz.murodjon.robotcallv2.scenario.domain.enums.TransitionMode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Custom Jackson deserializer for {@link ScenarioDefinition}.
 *
 * <p>Supports both:
 * <ul>
 *   <li>Standard array representation: {@code "stages": [ { "id": "greeting", ... } ]}</li>
 *   <li>Retell-style map representation: {@code "stages": { "greeting": { "purpose": ... } }}
 *       where the map key is used as the stage {@code id} if missing.</li>
 * </ul>
 * Also aliases {@code initialStage} to {@code startStageId} and provides safe defaults.
 */
public class ScenarioDefinitionDeserializer extends JsonDeserializer<ScenarioDefinition> {

    @Override
    public ScenarioDefinition deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        if (node == null || node.isNull()) {
            return null;
        }

        ObjectMapper mapper = (ObjectMapper) p.getCodec();

        List<StageDef> stages = new ArrayList<>();
        JsonNode stagesNode = node.get("stages");
        if (stagesNode != null && stagesNode.isArray()) {
            for (JsonNode sn : stagesNode) {
                stages.add(mapper.treeToValue(sn, StageDef.class));
            }
        } else if (stagesNode != null && stagesNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = stagesNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                StageDef st = mapper.treeToValue(entry.getValue(), StageDef.class);
                if (st != null) {
                    if (st.id() == null || st.id().isBlank()) {
                        st = st.toBuilder().id(entry.getKey()).build();
                    }
                    stages.add(st);
                }
            }
        }

        String callPurpose = node.hasNonNull("callPurpose") ? node.get("callPurpose").asText() : null;
        String startStageId = node.hasNonNull("startStageId") ? node.get("startStageId").asText()
                : (node.hasNonNull("initialStage") ? node.get("initialStage").asText() : null);

        String rolePrompt = node.hasNonNull("rolePrompt") ? node.get("rolePrompt").asText() : "Siz professional AI assistsiz.";
        String disclosureText = node.hasNonNull("disclosureText") ? node.get("disclosureText").asText() : null;

        TransitionMode transitionMode = TransitionMode.RIGID;
        if (node.hasNonNull("transitionMode")) {
            try {
                transitionMode = TransitionMode.valueOf(node.get("transitionMode").asText().toUpperCase());
            } catch (Exception ignored) {
            }
        }

        List<FactField> factSchema = readList(mapper, node.get("factSchema"), FactField.class);
        List<ToolDef> tools = readList(mapper, node.get("tools"), ToolDef.class);
        List<OutcomeField> outcomeSchema = readList(mapper, node.get("outcomeSchema"), OutcomeField.class);
        List<String> guardrails = readStringList(node.get("guardrails"));
        List<StyleExample> styleExamples = readList(mapper, node.get("styleExamples"), StyleExample.class);
        List<GlobalNode> globalNodes = readList(mapper, node.get("globalNodes"), GlobalNode.class);

        FactWebhook factWebhook = null;
        if (node.hasNonNull("factWebhook")) {
            factWebhook = mapper.treeToValue(node.get("factWebhook"), FactWebhook.class);
        }

        return new ScenarioDefinition(
                stages,
                factSchema,
                tools,
                outcomeSchema,
                rolePrompt,
                guardrails,
                disclosureText,
                factWebhook,
                styleExamples,
                globalNodes,
                callPurpose,
                startStageId,
                transitionMode
        );
    }

    private static <T> List<T> readList(ObjectMapper mapper, JsonNode node, Class<T> clazz) throws IOException {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<T> list = new ArrayList<>();
        for (JsonNode item : node) {
            list.add(mapper.treeToValue(item, clazz));
        }
        return list;
    }

    private static List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        for (JsonNode item : node) {
            list.add(item.asText());
        }
        return list;
    }
}
