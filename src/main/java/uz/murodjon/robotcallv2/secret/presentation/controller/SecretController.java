package uz.murodjon.robotcallv2.secret.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.secret.application.dto.CreateSecretRequest;
import uz.murodjon.robotcallv2.secret.application.dto.SecretRow;
import uz.murodjon.robotcallv2.secret.application.dto.UpdateSecretRequest;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * The company's secret vault — the API keys and tokens a tool or webhook refers to as
 * {@code {{secrets.KEY}}} rather than carrying in its own configuration.
 *
 * <p>No endpoint here ever returns a value: reads come back masked, and the plaintext
 * leaves the vault only inside the process, when a tool call is being built. Guarded by
 * the integration permissions — a secret exists to plug an external system in.
 */
@RequestMapping("/api/secrets")
public interface SecretController {

    @PreAuthorize("hasAuthority('INTEGRATION_READ')")
    @GetMapping
    ResponseEntity<ResponseData<List<SecretRow>>> list(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('INTEGRATION_READ')")
    @GetMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<SecretRow>> get(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('INTEGRATION_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<SecretRow>> create(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody CreateSecretRequest request);

    /** A blank {@code value} keeps the stored one, so a rename need not resend the secret. */
    @PreAuthorize("hasAuthority('INTEGRATION_EDIT')")
    @PutMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<SecretRow>> update(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody UpdateSecretRequest request);

    @PreAuthorize("hasAuthority('INTEGRATION_EDIT')")
    @DeleteMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<Void>> delete(
            @CurrentCompanyId long companyId,
            @PathVariable long id);
}
