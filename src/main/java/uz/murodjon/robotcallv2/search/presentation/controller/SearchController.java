package uz.murodjon.robotcallv2.search.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import uz.murodjon.robotcallv2.search.application.dto.SearchResult;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Command palette backend (UI-DESIGN §11.9, §9 topbar). Every hit is scoped to the
 * caller's company, and each group only to a caller who may read it: campaigns need
 * CAMPAIGN_READ, calls (phone numbers) need CALL_READ — it used to hand both to any
 * logged-in user or API key.
 */
@RequestMapping("/api")
public interface SearchController {

    @PreAuthorize("hasAnyAuthority('CALL_READ', 'CAMPAIGN_READ')")
    @GetMapping("/search")
    ResponseEntity<ResponseData<SearchResult>> search(@CurrentCompanyId long companyId, @RequestParam String q);
}
