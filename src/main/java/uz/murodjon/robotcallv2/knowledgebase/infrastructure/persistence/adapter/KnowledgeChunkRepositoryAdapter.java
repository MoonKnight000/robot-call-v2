package uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeChunkRepository;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.IndexedChunk;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeChunk;
import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceStatus;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.entity.KnowledgeChunkEntity;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.repository.KnowledgeChunkJpaRepository;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.repository.KnowledgeSourceJpaRepository;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@Component
public class KnowledgeChunkRepositoryAdapter implements KnowledgeChunkRepository {

    private final KnowledgeChunkJpaRepository jpaRepository;
    private final KnowledgeSourceJpaRepository knowledgeSourceJpaRepository;

    public KnowledgeChunkRepositoryAdapter(KnowledgeChunkJpaRepository jpaRepository,
                                           KnowledgeSourceJpaRepository knowledgeSourceJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.knowledgeSourceJpaRepository = knowledgeSourceJpaRepository;
    }

    @Override
    @Transactional
    public void replaceBySourceId(long sourceId, List<KnowledgeChunk> chunks) {
        jpaRepository.deleteBySourceId(sourceId);
        // Flushed before the inserts so the unique (source_id, ordinal) constraint sees
        // the deletes first; without it a re-index collides with the rows it is replacing.
        jpaRepository.flush();

        List<KnowledgeChunkEntity> entities = new ArrayList<>(chunks.size());
        for (KnowledgeChunk chunk : chunks) {
            KnowledgeChunkEntity entity = new KnowledgeChunkEntity();
            entity.setSource(knowledgeSourceJpaRepository.getReferenceById(sourceId));
            entity.setOrdinal(chunk.ordinal());
            entity.setContent(chunk.content());
            entity.setEmbedding(toBytes(chunk.embedding()));
            entities.add(entity);
        }
        jpaRepository.saveAll(entities);
    }

    @Override
    @Transactional
    public void deleteBySourceId(long sourceId) {
        jpaRepository.deleteBySourceId(sourceId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IndexedChunk> findIndexedByCompanyId(long companyId) {
        List<Object[]> rows = jpaRepository.findIndexedByCompanyId(companyId, KnowledgeSourceStatus.INDEXED);
        List<IndexedChunk> chunks = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Number agentId = (Number) row[1];
            chunks.add(new IndexedChunk(
                    ((Number) row[0]).longValue(),
                    agentId != null ? agentId.longValue() : null,
                    (String) row[2],
                    (String) row[3],
                    toFloats((byte[]) row[4])
            ));
        }
        return chunks;
    }

    /**
     * A vector as big-endian float32s, which is what {@link ByteBuffer} writes by default
     * — the layout is fixed here rather than left to the platform so a database written on
     * one machine reads correctly on another.
     */
    private static byte[] toBytes(float[] vector) {
        if (vector == null || vector.length == 0) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES);
        for (float value : vector) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    private static float[] toFloats(byte[] bytes) {
        if (bytes == null || bytes.length < Float.BYTES) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        float[] vector = new float[bytes.length / Float.BYTES];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }
}
