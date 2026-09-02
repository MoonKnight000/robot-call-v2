package uz.murodjon.robotcallv2.siptrunk.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.siptrunk.application.dto.*;

import java.util.List;

/**
 * Per-company SIP trunk CRUD and live registration/connectivity status (ROADMAP B.3).
 */
@RequestMapping("/api")
public interface SipTrunkController {

    @PostMapping("/sip-trunks")
    ResponseEntity<ResponseData<SipTrunkRow>> create(@Valid @RequestBody CreateSipTrunkRequest r);

    @PostMapping("/sip-trunks/list")
    ResponseEntity<ResponseData<PageableData<SipTrunkRow>>> list(@Valid @RequestBody SipTrunkFilter filter);

    @GetMapping("/sip-trunks/{id}")
    ResponseEntity<ResponseData<SipTrunkRow>> get(@PathVariable long id);

    @GetMapping("/sip-trunks/{id}/status")
    ResponseEntity<ResponseData<SipTrunkStatus>> getStatus(@PathVariable long id);

    @GetMapping("/sip-trunks/status")
    ResponseEntity<ResponseData<List<SipTrunkStatus>>> getAllStatuses();

    @PutMapping("/sip-trunks/{id}")
    ResponseEntity<ResponseData<SipTrunkRow>> update(@PathVariable long id, @Valid @RequestBody UpdateSipTrunkRequest r);

    @PostMapping("/sip-trunks/{id}/default")
    ResponseEntity<ResponseData<SipTrunkRow>> makeDefault(@PathVariable long id);

    @DeleteMapping("/sip-trunks/{id}")
    ResponseEntity<ResponseData<SipTrunkDeleteResponse>> delete(@PathVariable long id);
}
