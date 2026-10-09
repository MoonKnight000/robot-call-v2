package uz.murodjon.robotcallv2.wrapup.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.wrapup.application.dto.SaveWrapUpCodeRequest;
import uz.murodjon.robotcallv2.wrapup.domain.entity.WrapUpCode;
import uz.murodjon.robotcallv2.wrapup.domain.entity.WrapUpCodeFilter;

/**
 * The company's wrap-up codes (docs/api/wrap-up-codes.md). Configured with the queues
 * ({@code CALL_QUEUE_EDIT}); read by every operator, who picks one after each call.
 */
@RequestMapping("/api/wrap-up-codes")
public interface WrapUpCodeController {

    @PreAuthorize("hasAuthority('CALL_QUEUE_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<WrapUpCode>> create(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody SaveWrapUpCodeRequest request);

    @PreAuthorize("hasAuthority('OPERATOR_READ')")
    @PostMapping("/list")
    ResponseEntity<ResponseData<PageableData<WrapUpCode>>> list(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody WrapUpCodeFilter filter);

    @PreAuthorize("hasAuthority('OPERATOR_READ')")
    @GetMapping("/{id}")
    ResponseEntity<ResponseData<WrapUpCode>> get(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('CALL_QUEUE_EDIT')")
    @PutMapping("/{id}")
    ResponseEntity<ResponseData<WrapUpCode>> update(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody SaveWrapUpCodeRequest request);

    @PreAuthorize("hasAuthority('CALL_QUEUE_EDIT')")
    @DeleteMapping("/{id}")
    ResponseEntity<ResponseData<Void>> delete(
            @CurrentCompanyId long companyId,
            @PathVariable long id);
}
