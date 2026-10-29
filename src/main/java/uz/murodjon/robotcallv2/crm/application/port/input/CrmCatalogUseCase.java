package uz.murodjon.robotcallv2.crm.application.port.input;

import uz.murodjon.robotcallv2.crm.domain.entity.*;

import java.util.List;
import java.util.Optional;

/**
 * The company's Uysot data beyond the lead — complexes, flats on sale, contracts and their
 * payment schedules, employees and pipelines — for the agent's tools and the settings screen.
 *
 * <p>Every method throws {@code ConflictException CRM_NOT_CONNECTED} when the company has no
 * Uysot connection and {@code ExternalServiceException} when Uysot fails.
 */
public interface CrmCatalogUseCase {

    /** Whether the company is connected to Uysot — the agent's real-estate tools are offered only then. */
    boolean isUysotConnected(long companyId);

    List<CrmHouse> findHouses(long companyId);

    List<CrmFlat> searchFlats(long companyId, FlatSearchFilter filter);

    Optional<CrmContract> findContractByNumber(long companyId, String contractNumber);

    List<CrmContract> findActiveContractsByPhone(long companyId, String phone);

    List<CrmPaymentDue> findPaymentSchedule(long companyId, long contractId);

    List<CrmEmployee> findEmployees(long companyId);

    /** The employee with this CRM id, if the company has one. */
    Optional<CrmEmployee> findEmployeeById(long companyId, long employeeId);

    List<CrmPipe> findPipes(long companyId);

    /**
     * The person behind {@code phone}: their lead and active contracts.
     * {@code 400 PHONE_INVALID} for a number that is not one.
     */
    CrmClientProfile findClientProfile(long companyId, String phone);
}
