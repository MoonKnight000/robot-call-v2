package uz.murodjon.robotcallv2.storage.application.port.input;

import org.springframework.web.multipart.MultipartFile;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;
import uz.murodjon.robotcallv2.storage.domain.enums.FileCategory;

public interface ImageUploadUseCase {

    StoredFile uploadImage(MultipartFile file, FileCategory category, Long companyId);
}
