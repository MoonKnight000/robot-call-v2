package uz.murodjon.robotcallv2.search.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import uz.murodjon.robotcallv2.search.application.dto.SearchResult;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Command palette backend (UI-DESIGN §11.9, §9 topbar). Open to any logged-in user: every
 * hit is already scoped to the company of the caller, and the palette is how a user reaches
 * the pages their role does allow.
 */
@RequestMapping("/api")
public interface SearchController {

    @GetMapping("/search")
    ResponseEntity<ResponseData<SearchResult>> search(@RequestParam String q);
}
