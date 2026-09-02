package uz.murodjon.robotcallv2.storage.application.dto;

import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;
import uz.murodjon.robotcallv2.storage.domain.enums.FileCategory;

import java.time.Instant;

public record FileUploadResponse(
        long id,
        String originalName,
        String url,
        String contentType,
        long sizeBytes,
        FileCategory category,
        Instant createdAt
) {
    public static FileUploadResponse of(StoredFile file) {
        return new FileUploadResponse(
                file.id(),
                file.originalName(),
                "/api/files/" + file.id(),
                file.format(),
                file.sizeBytes(),
                file.category(),
                file.createdAt()
        );
    }
}
