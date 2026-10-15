package uz.murodjon.robotcallv2.report.domain.entity;

/**
 * The figures a manager reads the ROI page for, worked out from {@link RoiTotals} and
 * {@link RoiAssumptions} by {@code RoiCalculator}.
 *
 * @param costPerAnsweredCallUzs what one answered call cost; null with none answered
 * @param costPerPromiseUzs      what one promise cost; null with none won
 * @param operatorHours          hours operators would have needed for the same calls
 * @param operatorCostUzs        what those hours cost
 * @param savedUzs               operator cost less what the robot cost; negative when the robot cost more
 * @param operatorEquivalent     how many full-time operators that is over the period, one decimal
 * @param roiPercent             (conversion value − spend) / spend × 100, one decimal; null with nothing spent
 */
public record RoiFigures(
        Long costPerAnsweredCallUzs,
        Long costPerPromiseUzs,
        double operatorHours,
        long operatorCostUzs,
        long savedUzs,
        double operatorEquivalent,
        Double roiPercent
) {
}
