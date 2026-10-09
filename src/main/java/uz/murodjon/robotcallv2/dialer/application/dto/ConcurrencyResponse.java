package uz.murodjon.robotcallv2.dialer.application.dto;

/**
 * The company's concurrent dialer calls right now ({@code GET /api/concurrency}).
 *
 * @param limit           how many it may hold at once
 * @param platformDefault true when the limit is the platform's default, not one set for this company
 * @param active          calls the dialer is holding for it now
 * @param available       slots still free; 0 when the limit is reached
 */
public record ConcurrencyResponse(int limit, boolean platformDefault, int active, int available) {
}
