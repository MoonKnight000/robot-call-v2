package uz.murodjon.robotcallv2.wrapup.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.wrapup.application.dto.WrapUpCallRequest;
import uz.murodjon.robotcallv2.wrapup.domain.entity.CallWrapUp;

/**
 * An operator closing their call with a wrap-up code (docs/api/wrap-up-codes.md). Ends a
 * running WRAP_UP status, so the operator is offered the next call at once.
 */
@RequestMapping("/api/calls/{callId}/wrap-up")
public interface CallWrapUpController {

    @PreAuthorize("hasAuthority('OPERATOR_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<CallWrapUp>> wrapUp(
            @CurrentCompanyId long companyId,
            @PathVariable long callId,
            @Valid @RequestBody WrapUpCallRequest request);
}
