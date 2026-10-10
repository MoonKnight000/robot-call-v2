package uz.murodjon.robotcallv2.siptrunk.application.dto;

import jakarta.validation.constraints.NotBlank;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.CallerIdStrategy;

import java.util.List;

public record CreateCallerIdPoolRequest(
        @NotBlank String name,
        CallerIdStrategy strategy,
        String description,
        List<String> initialPhones
) {
}
