package uz.murodjon.robotcallv2.integration.application.port.input;

import uz.murodjon.robotcallv2.shared.integration.IntegrationProvider;
import uz.murodjon.robotcallv2.storage.application.dto.DownloadableFile;

/**
 * The logo of an integration provider, which every company is shown. The logo is set with the
 * integration itself ({@code iconFileId} of {@code IntegrationCatalogUseCase}); this only serves it.
 */
public interface IntegrationIconUseCase {

    /** The provider's logo; 404 while it has none. */
    DownloadableFile findIcon(IntegrationProvider provider);
}
