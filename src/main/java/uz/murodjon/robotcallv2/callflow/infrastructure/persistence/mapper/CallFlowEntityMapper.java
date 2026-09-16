package uz.murodjon.robotcallv2.callflow.infrastructure.persistence.mapper;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlow;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlowGraph;
import uz.murodjon.robotcallv2.callflow.infrastructure.persistence.entity.CallFlowEntity;

/**
 * Call flow rows ↔ domain. The graph travels as JSON: unknown fields are ignored so a flow
 * saved by a newer release still loads, and nulls are left out so the stored document is
 * the one the editor sent.
 */
@Component
public class CallFlowEntityMapper {

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public CallFlow toCallFlow(CallFlowEntity entity) {
        return new CallFlow(entity.getId(), entity.getCompanyId(), entity.getName(), entity.getDescription(),
                readGraph(entity.getDraftGraph()), readGraph(entity.getPublishedGraph()), entity.getVersion(),
                entity.getPublishedAt(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public String writeGraph(CallFlowGraph graph) {
        try {
            return JSON.writeValueAsString(graph);
        } catch (JsonProcessingException e) {
            // Cannot happen: the graph is a record of plain values the service already validated.
            throw new IllegalStateException("Call flow graph cannot be serialised", e);
        }
    }

    public CallFlowGraph readGraph(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(json, CallFlowGraph.class);
        } catch (JsonProcessingException e) {
            // Only a corrupted or hand-edited row gets here — a server fault, not a bad request.
            throw new IllegalStateException("Stored call flow graph cannot be read", e);
        }
    }
}
