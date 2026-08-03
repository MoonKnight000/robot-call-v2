package uz.murodjon.uysotvoice.inbound.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.uysotvoice.inbound.dto.CreateInboundRouteRequest;
import uz.murodjon.uysotvoice.inbound.dto.InboundRouteFilter;
import uz.murodjon.uysotvoice.inbound.dto.InboundRouteRow;
import uz.murodjon.uysotvoice.inbound.dto.UpdateInboundRouteRequest;
import uz.murodjon.uysotvoice.report.dto.InboundRouteStats;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

/**
 * Inbound DID routing API (ROADMAP C.1): which scenario a call to a given number runs.
 */
@RequestMapping("/api")
public interface InboundRouteController {

    @PostMapping("/inbound-routes")
    ResponseEntity<ResponseData<InboundRouteRow>> create(@Valid @RequestBody CreateInboundRouteRequest r);

    @PostMapping("/inbound-routes/list")
    ResponseEntity<ResponseData<PageableData<InboundRouteRow>>> list(@Valid @RequestBody InboundRouteFilter filter);

    @GetMapping("/inbound-routes/{id}")
    ResponseEntity<ResponseData<InboundRouteRow>> get(@PathVariable long id);

    @PutMapping("/inbound-routes/{id}")
    ResponseEntity<ResponseData<InboundRouteRow>> update(@PathVariable long id, @Valid @RequestBody UpdateInboundRouteRequest r);

    /** Soft-delete (mirrors {@code DELETE /api/campaigns/{id}}) — disables, never deletes. */
    @DeleteMapping("/inbound-routes/{id}")
    ResponseEntity<ResponseData<InboundRouteRow>> disable(@PathVariable long id);

    /** "Shu raqamga tushgan qo'ng'iroqlar statistikasi" (§10.9 drawer). */
    @GetMapping("/inbound-routes/{id}/stats")
    ResponseEntity<ResponseData<InboundRouteStats>> stats(@PathVariable long id);
}
