package uz.murodjon.robotcallv2.shared.api;

import java.util.List;
import java.util.function.Function;

/** The {@code data} payload of a paginated API (project API standard). */
public record PageableData<T>(int totalPages, int currentPage, long totalElements, List<T> data) {

    public static <T> PageableData<T> of(List<T> data, int currentPage, int pageSize, long totalElements) {
        int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);
        return new PageableData<>(totalPages, currentPage, totalElements, data);
    }

    /** The same page with each item converted; the paging figures stay as they are. */
    public <R> PageableData<R> map(Function<? super T, ? extends R> mapper) {
        return new PageableData<>(totalPages, currentPage, totalElements,
                data.stream().<R>map(mapper).toList());
    }
}
