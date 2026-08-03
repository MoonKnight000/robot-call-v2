package uz.murodjon.uysotvoice.search.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.uysotvoice.search.dto.SearchResult;
import uz.murodjon.uysotvoice.search.service.SearchService;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

@RestController
public class SearchControllerImpl implements SearchController {

    private final SearchService service;

    public SearchControllerImpl(SearchService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ResponseData<SearchResult>> search(String q) {
        return ResponseEntity.ok(ResponseData.ok(service.search(q)));
    }
}
