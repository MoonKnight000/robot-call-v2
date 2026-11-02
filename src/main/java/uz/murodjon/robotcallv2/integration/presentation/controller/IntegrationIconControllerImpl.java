package uz.murodjon.robotcallv2.integration.presentation.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.integration.application.port.input.IntegrationIconUseCase;
import uz.murodjon.robotcallv2.shared.api.FileResponseFactory;
import uz.murodjon.robotcallv2.shared.integration.IntegrationProvider;

@RestController
public class IntegrationIconControllerImpl implements IntegrationIconController {

    private final IntegrationIconUseCase integrationIconUseCase;
    private final FileResponseFactory fileResponseFactory;

    public IntegrationIconControllerImpl(IntegrationIconUseCase integrationIconUseCase,
                                         FileResponseFactory fileResponseFactory) {
        this.integrationIconUseCase = integrationIconUseCase;
        this.fileResponseFactory = fileResponseFactory;
    }

    @Override
    public ResponseEntity<Resource> download(IntegrationProvider provider) {
        return fileResponseFactory.toResponse(integrationIconUseCase.findIcon(provider), null);
    }
}
