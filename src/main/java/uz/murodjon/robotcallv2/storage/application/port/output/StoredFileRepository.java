package uz.murodjon.robotcallv2.storage.application.port.output;

import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;
import uz.murodjon.robotcallv2.storage.domain.enums.FileCategory;

import java.time.Instant;
import java.util.List;

/** Outbound port SPI for stored file repository. */
public interface StoredFileRepository {

    long create(long companyId, FileCategory category, String originalName, String path,
                String bucket, String format, long sizeBytes);

    StoredFile find(long id);

    List<StoredFile> findOlderThan(FileCategory category, Instant cutoff);

    void delete(long id);
}
