package uz.murodjon.robotcallv2.storage.application.port.input;

import uz.murodjon.robotcallv2.shared.api.UploadedFile;
import uz.murodjon.robotcallv2.storage.application.dto.DownloadableFile;
import uz.murodjon.robotcallv2.storage.application.dto.FileUploadResponse;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;
import uz.murodjon.robotcallv2.storage.domain.enums.FileCategory;

import java.nio.file.Path;

public interface FileStorageUseCase {

    DownloadableFile download(long callerCompanyId, long id);

    FileUploadResponse upload(long callerCompanyId, UploadedFile file, Long companyId, FileCategory category);

    /** Stores bytes the application produced itself; null when object storage refused them. */
    StoredFile store(byte[] data, long companyId, FileCategory category, String originalName, String contentType);

    /** Stores a file the application wrote to disk; null when object storage refused it. */
    StoredFile store(Path file, long companyId, FileCategory category, String originalName, String contentType);

    /**
     * Removes a stored file: the object in the bucket, then its row. A file of another
     * company is left alone. Answers whether it is gone; a bucket that refused keeps the
     * row, so the file can still be found and removed again.
     */
    boolean delete(long companyId, long id);
}
