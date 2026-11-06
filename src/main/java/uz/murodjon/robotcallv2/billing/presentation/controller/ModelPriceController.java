package uz.murodjon.robotcallv2.billing.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.billing.application.dto.ModelPriceResponse;
import uz.murodjon.robotcallv2.billing.application.dto.UpsertModelPriceRequest;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * The platform's per-model LLM / realtime price list (docs/api/platform-model-prices.md).
 * Prices are before the markup; a model without a row is billed at billing.yml's common rate.
 */
@RequestMapping("/api/platform/model-prices")
public interface ModelPriceController {

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @GetMapping
    ResponseEntity<ResponseData<List<ModelPriceResponse>>> findAll();

    /** Creates or replaces one model's prices. The id is stored lower-case, without a {@code models/} prefix. */
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PutMapping("/{model}")
    ResponseEntity<ResponseData<ModelPriceResponse>> upsert(
            @CurrentCompanyId long companyId,
            @PathVariable String model,
            @Valid @RequestBody UpsertModelPriceRequest request);

    /** The model falls back to the common rate. */
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @DeleteMapping("/{model}")
    ResponseEntity<ResponseData<Void>> delete(
            @CurrentCompanyId long companyId,
            @PathVariable String model);
}
