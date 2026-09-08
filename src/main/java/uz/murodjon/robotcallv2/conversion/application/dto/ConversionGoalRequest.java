package uz.murodjon.robotcallv2.conversion.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.conversion.domain.enums.AttributionModel;

/**
 * @param attributionWindowHours how long after a call a conversion still counts, 1..8760 (a year)
 * @param attributionModel       null keeps {@code LAST_CALL}
 */
public record ConversionGoalRequest(
        @NotBlank(message = "goalKey kiritilishi shart")
        @Size(max = 64, message = "goalKey 64 belgidan oshmasin")
        String goalKey,

        @NotBlank(message = "Maqsad nomi kiritilishi shart")
        @Size(max = 128, message = "Nom 128 belgidan oshmasin")
        String name,

        @Min(value = 1, message = "Atributsiya oynasi kamida 1 soat bo'lsin")
        @Max(value = 8760, message = "Atributsiya oynasi 8760 soatdan (1 yil) oshmasin")
        int attributionWindowHours,

        AttributionModel attributionModel,

        Boolean enabled
) {
}
