package uz.murodjon.robotcallv2.memory.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import uz.murodjon.robotcallv2.memory.application.dto.UpdateClientMemoryRequest;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
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
    ResponseEntity<ResponseData<ClientMemory>> get(@PathVariable String phone);

    @PreAuthorize("hasAuthority('CONTACT_EDIT')")
    @PutMapping("/{phone}")
    ResponseEntity<ResponseData<ClientMemory>> update(@PathVariable String phone,
                                                      @Valid @RequestBody UpdateClientMemoryRequest request);
}
