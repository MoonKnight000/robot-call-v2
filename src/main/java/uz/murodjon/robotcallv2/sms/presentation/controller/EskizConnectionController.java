package uz.murodjon.robotcallv2.sms.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.sms.application.dto.ConnectEskizRequest;
import uz.murodjon.robotcallv2.sms.application.dto.EskizConnectionRow;

/** The company's own Eskiz account, which its SMS are sent from. */
@RequestMapping("/api/settings/integrations/eskiz")
public interface EskizConnectionController {

    /** Whether the company has connected an Eskiz account; the password is never returned. */
    @PreAuthorize("hasAuthority('INTEGRATION_READ')")
    @GetMapping
    ResponseEntity<ResponseData<EskizConnectionRow>> get(@CurrentCompanyId long companyId);

    /** Logs in to Eskiz with the given account and, when it is accepted, saves it encrypted. */
    @PreAuthorize("hasAuthority('INTEGRATION_EDIT')")
    @PutMapping
    ResponseEntity<ResponseData<EskizConnectionRow>> connect(
            @CurrentCompanyId long companyId,
            @RequestBody ConnectEskizRequest request);

    /** Forgets the account. */
    @PreAuthorize("hasAuthority('INTEGRATION_EDIT')")
    @DeleteMapping
    ResponseEntity<ResponseData<Void>> disconnect(@CurrentCompanyId long companyId);
}
