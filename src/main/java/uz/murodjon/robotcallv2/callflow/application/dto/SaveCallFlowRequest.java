package uz.murodjon.robotcallv2.callflow.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlowGraph;

/**
 * Body of POST/PUT /api/call-flows — saves the draft. A draft may be incomplete; only
 * publishing requires it to be valid.
 */
public record SaveCallFlowRequest(
        @NotBlank @Size(max = 255) String name,
        String description,
        @NotNull CallFlowGraph graph
) {
}
