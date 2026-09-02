package uz.murodjon.robotcallv2.search.application.dto;

import java.util.List;

public record SearchResult(
        List<SearchItem> campaigns,
        List<SearchItem> calls
) {
}
