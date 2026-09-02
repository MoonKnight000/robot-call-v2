package uz.murodjon.robotcallv2.search.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

/** Domain validator for search query input. */
public final class SearchValidator {

    private SearchValidator() {
    }

    public static void validateQuery(String q) {
        if (q == null || q.isBlank()) {
            throw new ValidationException(ErrorCode.SEARCH_QUERY_BLANK);
        }
    }
}
