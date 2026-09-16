package uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeChunkRepository;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.IndexedChunk;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeChunk;
import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceStatus;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.entity.KnowledgeChunkEntity;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.mapper.KnowledgeChunkEntityMapper;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.repository.KnowledgeChunkJpaRepository;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.repository.KnowledgeSourceJpaRepository;

import java.util.ArrayList;
import java.util.List;

@Component
public class KnowledgeChunkRepositoryAdapter implements KnowledgeChunkRepository {

    private final KnowledgeChunkJpaRepository jpaRepository;
    private final KnowledgeChunkEntityMapper mapper;
    private final KnowledgeSourceJpaRepository knowledgeSourceJpaRepository;

    public KnowledgeChunkRepositoryAdapter(KnowledgeChunkJpaRepository jpaRepository,
                                           KnowledgeChunkEntityMapper mapper,
                                           KnowledgeSourceJpaRepository knowledgeSourceJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
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
            mapper.applyToEntity(chunk, entity);
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
        return jpaRepository.findIndexedByCompanyId(companyId, KnowledgeSourceStatus.INDEXED).stream()
                .map(mapper::toIndexedChunk)
                .toList();
    }
}
