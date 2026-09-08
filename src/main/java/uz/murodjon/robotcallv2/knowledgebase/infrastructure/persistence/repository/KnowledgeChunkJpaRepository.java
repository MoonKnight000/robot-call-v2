package uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceStatus;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.entity.KnowledgeChunkEntity;

import java.util.List;

@Repository
public interface KnowledgeChunkJpaRepository extends JpaRepository<KnowledgeChunkEntity, Long> {

    void deleteBySourceId(long sourceId);

    /**
     * Every chunk of every source the company has finished indexing, with the document's
     * name and the agent it belongs to.
     *
     * <p>Rows are {@code [sourceId, agentId, sourceName, content, embedding]}. A projection
     * rather than entities because the caller keeps thousands of these in memory and has no
     * use for a managed entity or for the chunk's own id.
     */
    @Query("SELECT s.id, s.agent.id, s.name, c.content, c.embedding "
            + "FROM KnowledgeChunkEntity c JOIN c.source s "
            + "WHERE s.company.id = :companyId AND s.status = :status "
            + "ORDER BY s.id, c.ordinal")
    List<Object[]> findIndexedByCompanyId(@Param("companyId") long companyId,
                                          @Param("status") KnowledgeSourceStatus status);
}
