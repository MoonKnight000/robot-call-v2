package uz.murodjon.uysotvoice.integration.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import uz.murodjon.uysotvoice.integration.dto.AuthorizeUrlResponse;
import uz.murodjon.uysotvoice.integration.dto.ConnectIntegrationRequest;
import uz.murodjon.uysotvoice.integration.dto.CrmCatalogEntry;
import uz.murodjon.uysotvoice.integration.dto.CrmIntegrationRow;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

import java.util.List;

/**
 * Uysot CRM OAuth connection (§11 settings) — scoped to the caller's own company via
 * {@code CurrentCompany} (JWT), not a path id, except the callback which carries no
 * identity at all (see its own javadoc). ADMIN-only except the callback ({@code
 * SecurityConfig}).
 */
@RequestMapping("/api/settings/integrations")
public interface IntegrationController {

    /** "Qaysi CRM'larga ulanish mumkin" ro'yxati (report #10) — static, kompaniyaga bog'liq emas. */
    @GetMapping("/catalog")
    ResponseEntity<ResponseData<List<CrmCatalogEntry>>> catalog();

    @GetMapping
    ResponseEntity<ResponseData<CrmIntegrationRow>> get();

    @PutMapping("/uysot")
    ResponseEntity<ResponseData<CrmIntegrationRow>> connect(@Valid @RequestBody ConnectIntegrationRequest r);

    @GetMapping("/uysot/authorize-url")
    ResponseEntity<ResponseData<AuthorizeUrlResponse>> authorizeUrl();

    /**
     * The browser lands here straight from Uysot's redirect — no {@code X-Api-Key}, no
     * {@code Authorization} header. {@code state} (signed, carrying the company id) is
     * what authenticates this request instead; {@code SecurityConfig} permits it
     * unauthenticated for exactly that reason.
     */
    @GetMapping("/uysot/callback")
    ResponseEntity<ResponseData<Void>> callback(@RequestParam String code, @RequestParam String state);

    @DeleteMapping("/uysot")
    ResponseEntity<ResponseData<Void>> disconnect();
}
