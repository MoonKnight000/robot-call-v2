package uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.IndexedChunk;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeChunk;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.entity.KnowledgeChunkEntity;

import java.nio.ByteBuffer;

/**
 * Maps a {@link KnowledgeChunk} onto its row and an indexed-chunk projection row back to an
 * {@link IndexedChunk}; the embedding travels as a byte array.
 */
@Component
public class KnowledgeChunkEntityMapper {

    /** The chunk's own fields; the source reference is the adapter's to resolve. */
    public void applyToEntity(KnowledgeChunk chunk, KnowledgeChunkEntity entity) {
        entity.setOrdinal(chunk.ordinal());
        entity.setContent(chunk.content());
        entity.setEmbedding(toBytes(chunk.embedding()));
    }

    /** A {@code findIndexedByCompanyId} row: id, agent id, source name, content, embedding. */
    public IndexedChunk toIndexedChunk(Object[] row) {
        Number agentId = (Number) row[1];
        return new IndexedChunk(
                ((Number) row[0]).longValue(),
                agentId != null ? agentId.longValue() : null,
                (String) row[2],
                (String) row[3],
                toFloats((byte[]) row[4])
        );
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
