package uz.murodjon.robotcallv2.integration.presentation.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import uz.murodjon.robotcallv2.shared.integration.IntegrationProvider;

/** The logos of the integration providers; any signed-in user may read them. */
@RequestMapping("/api/integration-icons")
public interface IntegrationIconController {

    /** The provider's logo image; 404 while it has none. {@code iconUrl} in the catalog points here. */
    @GetMapping("/{provider}")
    ResponseEntity<Resource> download(@PathVariable IntegrationProvider provider);
}
