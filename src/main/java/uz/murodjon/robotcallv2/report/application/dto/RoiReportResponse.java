package uz.murodjon.robotcallv2.report.application.dto;

import uz.murodjon.robotcallv2.report.domain.entity.RoiAssumptions;
import uz.murodjon.robotcallv2.report.domain.entity.RoiFigures;
import uz.murodjon.robotcallv2.report.domain.entity.RoiTotals;

import java.time.Instant;

/**
 * {@code GET /api/reports/roi}: what the robot did, what it cost, and what operators would
 * have cost for the same calls.
 *
 * @param assumptions the operator yardstick the savings were worked out with
 */
public record RoiReportResponse(
        Instant from,
        Instant to,
        RoiTotals totals,
        RoiFigures figures,
        RoiAssumptions assumptions
) {
}
