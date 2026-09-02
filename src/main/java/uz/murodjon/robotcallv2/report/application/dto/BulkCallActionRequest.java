package uz.murodjon.robotcallv2.report.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * {@code POST /api/reports/calls/bulk} (§10.4 "Ommaviy amal paneli").
 *
 * @param action {@code retry} (re-queue each call's target for another attempt) or
 *               {@code dnc} (opt each call's phone number out); CSV export of a
 *               selection is handled by {@code POST /api/reports/calls/export} with
 *               {@link CallFilter#ids()} instead — that returns a file, which does not
 *               fit this endpoint's {@code ResponseData} envelope
 * @param ids    {@code call_attempt} ids to act on
 */
public record BulkCallActionRequest(
        @NotBlank String action,
        @NotEmpty List<Long> ids) {
}

