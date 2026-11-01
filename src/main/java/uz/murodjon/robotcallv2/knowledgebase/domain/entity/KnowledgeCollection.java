package uz.murodjon.robotcallv2.knowledgebase.domain.entity;

import java.time.Instant;

/**
 * A named knowledge base: sources grouped under one name, and attached to any number of agents.
 *
 * @param id 0 for one not yet saved
 */
public record KnowledgeCollection(long id, long companyId, String name, Instant createdAt) {
}
