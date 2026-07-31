package uz.murodjon.uysotvoice.shared.api;

import java.util.List;

/** The {@code data} payload of a paginated API (project API standard). */
public record PageableData<T>(int totalPages, int currentPage, long totalElements, List<T> data) {

    public static <T> PageableData<T> of(List<T> data, int currentPage, int pageSize, long totalElements) {
        int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);
        return new PageableData<>(totalPages, currentPage, totalElements, data);
    }
}
