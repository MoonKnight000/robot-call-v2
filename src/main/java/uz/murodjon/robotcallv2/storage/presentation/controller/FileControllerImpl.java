package uz.murodjon.robotcallv2.storage.presentation.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.storage.application.dto.FileUploadResponse;
import uz.murodjon.robotcallv2.storage.application.port.input.FileStorageUseCase;
import uz.murodjon.robotcallv2.storage.domain.enums.FileCategory;

@RestController
public class FileControllerImpl implements FileController {

    private final FileStorageUseCase fileStorageUseCase;
    private final FileResponseFactory fileResponseFactory;

    public FileControllerImpl(FileStorageUseCase fileStorageUseCase, FileResponseFactory fileResponseFactory) {
        this.fileStorageUseCase = fileStorageUseCase;
        this.fileResponseFactory = fileResponseFactory;
    }

    @Override
    public ResponseEntity<Resource> download(long callerCompanyId, long id, String range) {
        return fileResponseFactory.toResponse(fileStorageUseCase.download(callerCompanyId, id), range);
    }

    @Override
    public ResponseEntity<ResponseData<FileUploadResponse>> upload(long callerCompanyId, MultipartFile file,
                                                                   FileCategory category, Long companyId) {
        return ResponseEntity.ok(ResponseData.ok(
                fileStorageUseCase.upload(callerCompanyId, file, companyId, category)));
    }
}
