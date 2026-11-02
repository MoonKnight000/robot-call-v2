package uz.murodjon.robotcallv2.integration.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.integration.application.port.input.IntegrationIconUseCase;
import uz.murodjon.robotcallv2.integration.application.port.output.IntegrationCatalogRepository;
import uz.murodjon.robotcallv2.integration.domain.entity.IntegrationDefinition;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.integration.IntegrationProvider;
import uz.murodjon.robotcallv2.storage.application.dto.DownloadableFile;
import uz.murodjon.robotcallv2.storage.application.port.input.FileStorageUseCase;

@Service
public class IntegrationIconService implements IntegrationIconUseCase {

    private final IntegrationCatalogRepository catalogRepository;
    private final FileStorageUseCase fileStorageUseCase;

    public IntegrationIconService(IntegrationCatalogRepository catalogRepository,
                                  FileStorageUseCase fileStorageUseCase) {
        this.catalogRepository = catalogRepository;
        this.fileStorageUseCase = fileStorageUseCase;
    }

    @Override
    public DownloadableFile findIcon(IntegrationProvider provider) {
        IntegrationDefinition definition = catalogRepository.findIntegration(provider.key())
                .orElseThrow(() -> new NotFoundException(ErrorCode.INTEGRATION_NOT_FOUND, provider.key()));
        if (definition.iconFileId() == null) {
            throw new NotFoundException(ErrorCode.INTEGRATION_ICON_NOT_FOUND, definition.key());
        }
        return fileStorageUseCase.read(definition.iconFileId());
    }
}
