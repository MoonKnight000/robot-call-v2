package uz.murodjon.robotcallv2.search.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.search.application.dto.SearchResult;
import uz.murodjon.robotcallv2.search.application.port.input.SearchUseCase;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class SearchControllerImpl implements SearchController {

    private final SearchUseCase searchUseCase;

    public SearchControllerImpl(SearchUseCase searchUseCase) {
        this.searchUseCase = searchUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<SearchResult>> search(String q) {
        return ResponseEntity.ok(ResponseData.ok(searchUseCase.search(q)));
    }
}
