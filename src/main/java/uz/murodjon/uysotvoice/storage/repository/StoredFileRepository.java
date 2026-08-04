package uz.murodjon.uysotvoice.storage.repository;

import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.storage.dto.StoredFile;
import uz.murodjon.uysotvoice.storage.entity.StoredFileEntity;
import uz.murodjon.uysotvoice.storage.enums.FileCategory;

import java.time.Instant;
import java.util.List;

/**
 * JPA-backed DAO for {@code stored_file}. Not scoped by {@code CurrentCompany} — callers
 * always pass {@code companyId} explicitly, since the nightly retention sweep ({@code
 * RetentionService}) runs outside any request/tenant context.
 */
@Repository
public class StoredFileRepository {

    private final StoredFileJpaRepository jpa;

    public StoredFileRepository(StoredFileJpaRepository jpa) {
        this.jpa = jpa;
    }

    public long create(long companyId, FileCategory category, String originalName, String path,
                        String bucket, String format, long sizeBytes) {
        StoredFileEntity entity = new StoredFileEntity();
        entity.setCompanyId(companyId);
        entity.setCategory(category);
        entity.setOriginalName(originalName);
        entity.setPath(path);
        entity.setBucket(bucket);
        entity.setFormat(format);
        entity.setSizeBytes(sizeBytes);
        entity.setCreatedAt(Instant.now());
        return jpa.save(entity).getId();
    }

    /**
     * Unscoped lookup — the retention sweep already filtered by category/age, and {@code
     * FileStorageService#download} pairs this with {@code CompanyAccessGuard} for the
     * tenant check instead of filtering here (same split as {@code CompanyService}).
     */
    public StoredFile find(long id) {
        return jpa.findById(id).map(StoredFileRepository::toRow).orElse(null);
    }

    public List<StoredFile> findOlderThan(FileCategory category, Instant cutoff) {
        return jpa.findByCategoryAndCreatedAtBefore(category, cutoff).stream()
                .map(StoredFileRepository::toRow)
                .toList();
    }

    public void delete(long id) {
        jpa.deleteById(id);
    }

    private static StoredFile toRow(StoredFileEntity e) {
        return new StoredFile(e.getId(), e.getCompanyId(), e.getCategory(), e.getOriginalName(),
                e.getPath(), e.getBucket(), e.getFormat(), e.getSizeBytes(), e.getCreatedAt());
    }
}
