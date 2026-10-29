package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmCatalogUseCase;
import uz.murodjon.robotcallv2.crm.domain.entity.*;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The agent states prices and balances only as the CRM gave them, and a CRM that fails
 * must read as "not available" rather than an error the model relays.
 */
class RealEstateToolsTest {

    private static final long COMPANY = 1L;

    private DialogSession session;
    private CrmCatalogUseCase catalog;
    private RealEstateTools tools;

    @BeforeEach
    void setUp() {
        session = DialogSession.builder("chan-1", "uz-UZ")
                .context(new CallContext(Map.of(), "goal").withPhone("+998901234567"))
                .scenario(ScenarioFixtures.debtCollection())
                .callAttemptId(42L)
                .companyId(COMPANY)
                .companyName("Uysot")
                .build();
        catalog = mock(CrmCatalogUseCase.class);
        tools = new RealEstateTools(session, COMPANY, catalog);
    }

    @Test
    void searchFlatsListsCrmPricesAndAllowsThem() {
        when(catalog.searchFlats(eq(COMPANY), any())).thenReturn(List.of(new CrmFlat(1, "Yunusobod Residence",
                "A blok", "45", 3, new BigDecimal("78.5"), 5, new BigDecimal("58900"), null, null, "USD", "SALE")));

        String answer = tools.searchFlats(3, new BigDecimal("60000"), null, null, null, null);

        assertThat(answer).contains("Yunusobod Residence").contains("58900 USD").contains("78.5 m2");
        assertThat(session.outcome()).containsEntry("roomsNeeded", "3 xonali");
        verify(catalog).searchFlats(COMPANY, new FlatSearchFilter(3, null, new BigDecimal("60000"), null, null, null, 3));
    }

    @Test
    void searchFlatsSaysNothingMatchesWithoutInventing() {
        when(catalog.searchFlats(eq(COMPANY), any())).thenReturn(List.of());

        assertThat(tools.searchFlats(5, null, null, null, null, null)).startsWith("NO FLATS ON SALE MATCH");
    }

    @Test
    void searchFlatsReadsAFailedCrmAsNotAvailable() {
        when(catalog.searchFlats(eq(COMPANY), any()))
                .thenThrow(new ExternalServiceException(ErrorCode.UYSOT_REQUEST_FAILED, "uysot", "flat", "HTTP 500"));

        assertThat(tools.searchFlats(2, null, null, null, null, null)).startsWith("THE DATA IS NOT AVAILABLE");
    }

    @Test
    void unknownComplexIsAskedAboutNotGuessed() {
        when(catalog.findHouses(COMPANY)).thenReturn(List.of(new CrmHouse(7, "Yunusobod Residence", true, null, null, null, List.of())));

        String answer = tools.searchFlats(2, null, null, "Chilonzor City", null, null);

        assertThat(answer).contains("No residential complex").contains("Yunusobod Residence");
        verify(catalog, never()).searchFlats(anyLong(), any());
    }

    @Test
    void contractBalanceByPhoneStatesRemainingAndNextPayment() {
        CrmContract contract = new CrmContract(10, "UY-123", "Aziz", new BigDecimal("80000"), new BigDecimal("30000"),
                new BigDecimal("50000"), "USD", 12, "ACTIVE", 99L, 252L, "Dilshod Karimov");
        when(catalog.findActiveContractsByPhone(COMPANY, "+998901234567")).thenReturn(List.of(contract));
        when(catalog.findPaymentSchedule(COMPANY, 10)).thenReturn(List.of(
                new CrmPaymentDue(LocalDate.of(2026, 9, 1), new BigDecimal("2000"), BigDecimal.ZERO, true),
                new CrmPaymentDue(LocalDate.of(2026, 10, 1), new BigDecimal("2000"), new BigDecimal("2000"), false)));

        String answer = tools.getContractBalance(null);

        assertThat(answer).contains("remaining 50000 USD").contains("overdue by 12 days")
                .contains("2026-10-01").contains("2000 USD");
        assertThat(session.outcome()).containsEntry("responsibleEmployeeId", 252L)
                .containsEntry("contractNumber", "UY-123");
    }

    @Test
    void contractBalanceWithoutContractRefusesToInvent() {
        when(catalog.findContractByNumber(COMPANY, "X-1")).thenReturn(Optional.empty());
        when(catalog.findActiveContractsByPhone(eq(COMPANY), any())).thenReturn(List.of());

        assertThat(tools.getContractBalance("X-1")).startsWith("NO CONTRACT FOUND");
    }

    @Test
    void managerCallbackIsRecordedForTheCrmTask() {
        String answer = tools.requestManagerCallback("chegirma haqida", "ertaga 10:00");

        assertThat(answer).contains("manager");
        assertThat(session.outcome()).containsEntry("managerCallbackRequested", true)
                .containsEntry("managerCallbackReason", "chegirma haqida")
                .containsEntry("managerCallbackTime", "ertaga 10:00");
    }
}
