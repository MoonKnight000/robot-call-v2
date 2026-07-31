package uz.murodjon.uysotvoice.report.dto;

/** One slice of the "Natijalar taqsimoti" distribution (§10.2 UI-DESIGN.md). */
public record DashboardOutcome(String disposition, long count) {
}
