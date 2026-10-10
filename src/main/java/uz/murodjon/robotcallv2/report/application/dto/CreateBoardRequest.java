package uz.murodjon.robotcallv2.report.application.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateBoardRequest(
        @NotBlank String name,
        String description,
        boolean isDefault
) {
}
