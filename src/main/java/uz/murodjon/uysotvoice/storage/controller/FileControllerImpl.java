package uz.murodjon.uysotvoice.storage.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.uysotvoice.storage.service.FileStorageService;

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
}
