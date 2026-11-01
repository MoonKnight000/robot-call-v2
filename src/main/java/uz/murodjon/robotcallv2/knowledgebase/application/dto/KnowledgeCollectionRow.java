package uz.murodjon.robotcallv2.knowledgebase.application.dto;

import java.time.Instant;
import java.util.List;

public record KnowledgeCollectionRow(
        long id,
        String name,
        int sourceCount,
        List<Long> agentIds,
        Instant createdAt
) {
}
