package uz.murodjon.uysotvoice.search.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import uz.murodjon.uysotvoice.search.dto.SearchResult;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

/** Command palette backend (UI-DESIGN §11.9, §9 topbar). */
@RequestMapping("/api")
public interface SearchController {

    @GetMapping("/search")
    ResponseEntity<ResponseData<SearchResult>> search(@RequestParam String q);
}
