package uz.murodjon.robotcallv2.storage.application.port.input;

import uz.murodjon.robotcallv2.shared.api.UploadedFile;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;

public interface ImageUploadUseCase {

    /** @param companyId the owning company — namespaces the MinIO object key */
    StoredFile upload(UploadedFile file, long companyId);
}
