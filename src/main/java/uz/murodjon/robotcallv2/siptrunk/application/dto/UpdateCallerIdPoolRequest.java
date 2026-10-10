package uz.murodjon.robotcallv2.siptrunk.application.dto;

import jakarta.validation.constraints.NotBlank;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.CallerIdStrategy;

public record UpdateCallerIdPoolRequest(
        @NotBlank String name,
        CallerIdStrategy strategy,
        String description
) {
}
