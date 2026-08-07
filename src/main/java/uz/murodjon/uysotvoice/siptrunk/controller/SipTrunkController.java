package uz.murodjon.uysotvoice.siptrunk.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.api.ResponseData;
import uz.murodjon.uysotvoice.siptrunk.dto.CreateSipTrunkRequest;
import uz.murodjon.uysotvoice.siptrunk.dto.SipTrunkDeleteResponse;
import uz.murodjon.uysotvoice.siptrunk.dto.SipTrunkFilter;
import uz.murodjon.uysotvoice.siptrunk.dto.SipTrunkRow;
import uz.murodjon.uysotvoice.siptrunk.dto.UpdateSipTrunkRequest;

/** Per-company SIP trunk CRUD (ROADMAP B.3): which PJSIP endpoint(s) a company's calls go out on. */
@RequestMapping("/api")
public interface SipTrunkController {

    @PostMapping("/sip-trunks")
    ResponseEntity<ResponseData<SipTrunkRow>> create(@Valid @RequestBody CreateSipTrunkRequest r);

    @PostMapping("/sip-trunks/list")
    ResponseEntity<ResponseData<PageableData<SipTrunkRow>>> list(@Valid @RequestBody SipTrunkFilter filter);

    @GetMapping("/sip-trunks/{id}")
    ResponseEntity<ResponseData<SipTrunkRow>> get(@PathVariable long id);

    @PutMapping("/sip-trunks/{id}")
    ResponseEntity<ResponseData<SipTrunkRow>> update(@PathVariable long id, @Valid @RequestBody UpdateSipTrunkRequest r);

    /** Promotes {@code id} to the company's default trunk. Body-less. */
    @PostMapping("/sip-trunks/{id}/default")
    ResponseEntity<ResponseData<SipTrunkRow>> makeDefault(@PathVariable long id);

    /** Rejected with {@code 409} while {@code id} is the default trunk — promote another one first. */
    @DeleteMapping("/sip-trunks/{id}")
    ResponseEntity<ResponseData<SipTrunkDeleteResponse>> delete(@PathVariable long id);
}
