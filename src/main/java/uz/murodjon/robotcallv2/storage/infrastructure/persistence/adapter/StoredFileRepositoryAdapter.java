package uz.murodjon.robotcallv2.storage.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.storage.application.mapper.StoredFileMapper;
import uz.murodjon.robotcallv2.storage.application.port.output.StoredFileRepository;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;
import uz.murodjon.robotcallv2.storage.domain.enums.FileCategory;
import uz.murodjon.robotcallv2.storage.infrastructure.persistence.entity.StoredFileEntity;
import uz.murodjon.robotcallv2.storage.infrastructure.persistence.repository.StoredFileJpaRepository;

import java.time.Instant;
import java.util.List;

@Component
public class StoredFileRepositoryAdapter implements StoredFileRepository {

    private final StoredFileJpaRepository jpaRepository;
    private final StoredFileMapper mapper;
    private final CompanyJpaRepository companyJpaRepository;

    public StoredFileRepositoryAdapter(StoredFileJpaRepository jpaRepository, StoredFileMapper mapper,
                                       CompanyJpaRepository companyJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.companyJpaRepository = companyJpaRepository;
    }

    @Override
    public long create(long companyId, FileCategory category, String originalName, String path,
                       String bucket, String format, long sizeBytes) {
        StoredFileEntity entity = new StoredFileEntity();
        entity.setCompany(companyJpaRepository.getReferenceById(companyId));
        entity.setCategory(category);
        entity.setOriginalName(originalName);
        entity.setPath(path);
        entity.setBucket(bucket);
        entity.setFormat(format);
        entity.setSizeBytes(sizeBytes);
        entity.setCreatedAt(Instant.now());
        return jpaRepository.save(entity).getId();
    }

    @Override
    public StoredFile find(long id) {
        return jpaRepository.findById(id).map(mapper::entityToDomain).orElse(null);
    }

    @Override
    public List<StoredFile> findOlderThan(FileCategory category, Instant cutoff) {
        return jpaRepository.findByCategoryAndCreatedAtBefore(category, cutoff).stream()
                .map(mapper::entityToDomain)
                .toList();
    }

    @Override
    public void delete(long id) {
        jpaRepository.deleteById(id);
    }
}
