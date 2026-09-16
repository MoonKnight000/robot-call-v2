package uz.murodjon.robotcallv2.callflow.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.callflow.application.dto.CallFlowValidationResponse;
import uz.murodjon.robotcallv2.callflow.application.dto.SaveCallFlowRequest;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlow;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlowFilter;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/** Call flows — the IVR / routing graph an inbound number runs (docs/api/call-flows.md). */
@RequestMapping("/api/call-flows")
public interface CallFlowController {

    @PreAuthorize("hasAuthority('CALL_FLOW_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<CallFlow>> create(@CurrentCompanyId long companyId,
                                                  @Valid @RequestBody SaveCallFlowRequest request);

    @PreAuthorize("hasAuthority('CALL_FLOW_READ')")
    @PostMapping("/list")
    ResponseEntity<ResponseData<PageableData<CallFlow>>> list(@CurrentCompanyId long companyId,
                                                              @Valid @RequestBody CallFlowFilter filter);

    @PreAuthorize("hasAuthority('CALL_FLOW_READ')")
    @GetMapping("/{id}")
    ResponseEntity<ResponseData<CallFlow>> get(@CurrentCompanyId long companyId, @PathVariable long id);

    /** Saves the draft only; calls keep running the published version. */
    @PreAuthorize("hasAuthority('CALL_FLOW_EDIT')")
    @PutMapping("/{id}")
    ResponseEntity<ResponseData<CallFlow>> update(@CurrentCompanyId long companyId, @PathVariable long id,
                                                  @Valid @RequestBody SaveCallFlowRequest request);

    @PreAuthorize("hasAuthority('CALL_FLOW_EDIT')")
    @DeleteMapping("/{id}")
    ResponseEntity<ResponseData<Void>> delete(@CurrentCompanyId long companyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('CALL_FLOW_READ')")
    @PostMapping("/{id}/validate")
    ResponseEntity<ResponseData<CallFlowValidationResponse>> validate(@CurrentCompanyId long companyId,
                                                                      @PathVariable long id);

    @PreAuthorize("hasAuthority('CALL_FLOW_EDIT')")
    @PostMapping("/{id}/publish")
    ResponseEntity<ResponseData<CallFlow>> publish(@CurrentCompanyId long companyId, @PathVariable long id);
}
