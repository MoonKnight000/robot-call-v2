package uz.murodjon.robotcallv2.dialer.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import uz.murodjon.robotcallv2.dialer.application.dto.ConcurrencyResponse;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * The company's concurrent dialer calls (docs/api/concurrency.md): its limit, how many it holds
 * now and how many slots are free. The limit is set per company by the platform
 * ({@code PUT /api/companies/{id}/concurrency-limit}).
 */
@RequestMapping("/api/concurrency")
public interface ConcurrencyController {

    @PreAuthorize("hasAuthority('LIVE_READ')")
    @GetMapping
    ResponseEntity<ResponseData<ConcurrencyResponse>> find(@CurrentCompanyId long companyId);
}
