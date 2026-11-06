package uz.murodjon.robotcallv2.memory.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.memory.application.dto.UpdateClientMemoryRequest;
import uz.murodjon.robotcallv2.memory.application.dto.UpdateRememberedCallRequest;
import uz.murodjon.robotcallv2.memory.application.dto.UpsertClientMemoryFactRequest;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * What the agent remembers about a client across calls, plus the operator's notes on
 * top of it. {@code phone} is any form {@code PhoneNumbers} accepts; a leading
 * {@code +} must be URL-encoded ({@code %2B}) or left out.
 */
@RequestMapping("/api/memory")
public interface ClientMemoryController {

    @PreAuthorize("hasAuthority('CONTACT_READ')")
    @GetMapping("/{phone}")
    ResponseEntity<ResponseData<ClientMemory>> get(
            @CurrentCompanyId long companyId,
            @PathVariable String phone);

    @PreAuthorize("hasAuthority('CONTACT_EDIT')")
    @PutMapping("/{phone}")
    ResponseEntity<ResponseData<ClientMemory>> update(
            @CurrentCompanyId long companyId,
            @PathVariable String phone,
            @Valid @RequestBody UpdateClientMemoryRequest request);

    /** Forgets everything about the client: notes, remembered conversations and facts. */
    @PreAuthorize("hasAuthority('CONTACT_EDIT')")
    @DeleteMapping("/{phone}")
    ResponseEntity<ResponseData<Void>> delete(
            @CurrentCompanyId long companyId,
            @PathVariable String phone);

    /** Sets one fact by hand; the agent hears it on the next call, marked as the operator's. */
    @PreAuthorize("hasAuthority('CONTACT_EDIT')")
    @PutMapping("/{phone}/facts/{key}")
    ResponseEntity<ResponseData<ClientMemory>> upsertFact(
            @CurrentCompanyId long companyId,
            @PathVariable String phone,
            @PathVariable String key,
            @Valid @RequestBody UpsertClientMemoryFactRequest request);

    @PreAuthorize("hasAuthority('CONTACT_EDIT')")
    @DeleteMapping("/{phone}/facts/{key}")
    ResponseEntity<ResponseData<ClientMemory>> deleteFact(
            @CurrentCompanyId long companyId,
            @PathVariable String phone,
            @PathVariable String key);

    /** Corrects what the agent will hear about one past conversation. */
    @PreAuthorize("hasAuthority('CONTACT_EDIT')")
    @PatchMapping("/{phone}/calls/{callId}")
    ResponseEntity<ResponseData<ClientMemory>> updateCall(
            @CurrentCompanyId long companyId,
            @PathVariable String phone,
            @PathVariable String callId,
            @Valid @RequestBody UpdateRememberedCallRequest request);

    @PreAuthorize("hasAuthority('CONTACT_EDIT')")
    @DeleteMapping("/{phone}/calls/{callId}")
    ResponseEntity<ResponseData<ClientMemory>> deleteCall(
            @CurrentCompanyId long companyId,
            @PathVariable String phone,
            @PathVariable String callId);
}
