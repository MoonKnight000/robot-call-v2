package uz.murodjon.robotcallv2.search.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import uz.murodjon.robotcallv2.search.application.dto.SearchResult;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/** Command palette backend (UI-DESIGN §11.9, §9 topbar). */
@RequestMapping("/api")
@PreAuthorize("hasAuthority('ADMIN')")
public interface SearchController {

    @GetMapping("/search")
    ResponseEntity<ResponseData<SearchResult>> search(@RequestParam String q);
}
