package uz.murodjon.uysotvoice.storage.dto;

import uz.murodjon.uysotvoice.storage.enums.FileCategory;

import java.time.Instant;

/** {@code stored_file} row, 1:1 with {@code StoredFileEntity}. */
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
