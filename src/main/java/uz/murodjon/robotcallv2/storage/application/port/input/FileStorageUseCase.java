package uz.murodjon.robotcallv2.storage.application.port.input;

import org.springframework.web.multipart.MultipartFile;
import uz.murodjon.robotcallv2.storage.application.dto.DownloadableFile;
import uz.murodjon.robotcallv2.storage.application.dto.FileUploadResponse;
import uz.murodjon.robotcallv2.storage.domain.enums.FileCategory;

public interface FileStorageUseCase {

    DownloadableFile download(long callerCompanyId, long id);

    FileUploadResponse upload(long callerCompanyId, MultipartFile file, Long companyId, FileCategory category);
}
