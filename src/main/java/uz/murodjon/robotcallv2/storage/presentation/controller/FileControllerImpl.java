package uz.murodjon.robotcallv2.storage.presentation.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.storage.application.dto.FileUploadResponse;
import uz.murodjon.robotcallv2.storage.application.service.FileStorageService;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;
import uz.murodjon.robotcallv2.storage.domain.enums.FileCategory;

@RestController
public class FileControllerImpl implements FileController {

    private final FileStorageService files;
    private final FileResponseFactory responseFactory;

    public FileControllerImpl(FileStorageService files, FileResponseFactory responseFactory) {
        this.files = files;
        this.responseFactory = responseFactory;
    }

    @Override
    public ResponseEntity<Resource> download(long id) {
        return responseFactory.toResponse(files.download(id));
    }

    @Override
    public ResponseEntity<ResponseData<FileUploadResponse>> upload(MultipartFile file, FileCategory category, Long companyId) {
        StoredFile stored = files.upload(file, companyId, category);
        return ResponseEntity.ok(ResponseData.ok(FileUploadResponse.of(stored)));
    }
}
