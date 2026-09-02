package uz.murodjon.robotcallv2.storage.domain.entity;

import uz.murodjon.robotcallv2.storage.domain.enums.FileCategory;

import java.time.Instant;

/**
 * Domain record for a stored file metadata record (1:1 with {@code stored_file}).
 */
public record StoredFile(
        long id,
        long companyId,
        FileCategory category,
        String originalName,
        String path,
        String bucket,
        String format,
        long sizeBytes,
        Instant createdAt
) {
}
