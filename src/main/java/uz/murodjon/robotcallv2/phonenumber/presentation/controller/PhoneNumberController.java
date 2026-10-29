package uz.murodjon.robotcallv2.phonenumber.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.phonenumber.application.dto.*;
import uz.murodjon.robotcallv2.phonenumber.domain.entity.PhoneNumberFilter;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Phone Number Marketplace, BYON and inbound routing management (Retell-style Elastic Telephony).
 */
@RequestMapping("/api/phone-numbers")
public interface PhoneNumberController {

    @PreAuthorize("hasAuthority('SIP_TRUNK_READ')")
    @PostMapping("/marketplace")
    ResponseEntity<ResponseData<PageableData<PhoneNumberRow>>> findMarketplaceNumbers(
            @Valid @RequestBody PhoneNumberFilter filter);

    @PreAuthorize("hasAuthority('SIP_TRUNK_EDIT')")
    @PostMapping("/purchase")
    ResponseEntity<ResponseData<PhoneNumberRow>> purchaseNumber(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody PurchaseNumberRequest request);

    @PreAuthorize("hasAuthority('SIP_TRUNK_READ')")
    @PostMapping("/list")
    ResponseEntity<ResponseData<PageableData<PhoneNumberRow>>> findCompanyNumbers(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody PhoneNumberFilter filter);

    @PreAuthorize("hasAuthority('SIP_TRUNK_EDIT')")
    @PutMapping("/{id}/routing")
    ResponseEntity<ResponseData<PhoneNumberRow>> updateInboundRouting(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody UpdateInboundRoutingRequest request);

    @PreAuthorize("hasAuthority('SIP_TRUNK_EDIT')")
    @DeleteMapping("/{id}")
    ResponseEntity<ResponseData<Void>> releaseNumber(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('SIP_TRUNK_EDIT')")
    @PostMapping("/test-connection")
    ResponseEntity<ResponseData<TestSipConnectionResponse>> testSipConnection(
            @Valid @RequestBody TestSipConnectionRequest request);
}
