package uz.murodjon.robotcallv2.company.application.dto;

import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;

public record UpdateCompanyStatusRequest(
        @NotNull CompanyStatus status
) {
}
