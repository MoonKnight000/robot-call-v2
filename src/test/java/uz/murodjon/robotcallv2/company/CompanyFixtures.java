package uz.murodjon.robotcallv2.company;

import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/** Test doubles for the company feature. */
public final class CompanyFixtures {

    private CompanyFixtures() {
    }

    /** A CompanyUseCase whose company is open and has every feature and integration switched on. */
    public static CompanyUseCase openCompanyUseCase() {
        CompanyUseCase companyUseCase = mock(CompanyUseCase.class);
        lenient().when(companyUseCase.isFeatureAvailable(anyLong(), any())).thenReturn(true);
        lenient().when(companyUseCase.isIntegrationAvailable(anyLong(), any())).thenReturn(true);
        return companyUseCase;
    }
}
