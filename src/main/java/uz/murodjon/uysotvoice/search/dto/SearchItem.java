package uz.murodjon.uysotvoice.search.dto;

/** One command-palette result row (UI-DESIGN §11.9): icon+label is client-side, this is the data. */
public record SearchItem(long id, String label, String context) {
}
