package uz.murodjon.robotcallv2.callflow.application.dto;

import java.util.List;

/** POST /api/call-flows/{id}/validate — whether the draft could be published, and if not, why. */
public record CallFlowValidationResponse(
        boolean valid,
        List<String> problems
) {
}
