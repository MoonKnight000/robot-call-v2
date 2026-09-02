package uz.murodjon.robotcallv2.storage.application.port.input;

import uz.murodjon.robotcallv2.storage.application.dto.DownloadableFile;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;
import uz.murodjon.robotcallv2.storage.domain.enums.FileCategory;

public interface FileStorageUseCase {

    DownloadableFile download(long id, FileCategory expectedCategory);

    DownloadableFile downloadPublic(long id, FileCategory expectedCategory);

    StoredFile upload(byte[] data, String originalFilename, String contentType, FileCategory category, Long companyId);

    StoredFile uploadPublic(byte[] data, String originalFilename, String contentType, FileCategory category);
}
