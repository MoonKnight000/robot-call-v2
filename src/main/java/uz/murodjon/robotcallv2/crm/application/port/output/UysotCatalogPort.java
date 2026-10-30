package uz.murodjon.robotcallv2.crm.application.port.output;

import uz.murodjon.robotcallv2.crm.domain.entity.*;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;

import java.util.List;
import java.util.Optional;

/**
 * Uysot Open API reads beyond the lead: complexes, flats, contracts, schedules, employees,
 * pipelines. Throws {@code ExternalServiceException} when Uysot fails or a path is not configured.
 */
public interface UysotCatalogPort {

    List<CrmHouse> findHouses(CrmConnection connection);

    /** Flats on sale matching the filter, cheapest first. */
    List<CrmFlat> searchFlats(CrmConnection connection, FlatSearchFilter filter);

    /** The contract with exactly this number, if the CRM has one. */
    Optional<CrmContract> findContractByNumber(CrmConnection connection, String contractNumber);

    /** Contracts still in force (ACTIVE, STARTED) whose client this phone belongs to. */
    List<CrmContract> findActiveContractsByPhone(CrmConnection connection, String phone);

    /** The active monthly payment schedule of a contract, in date order. */
    List<CrmPaymentDue> findPaymentSchedule(CrmConnection connection, long contractId);

    List<CrmEmployee> findEmployees(CrmConnection connection);

    List<CrmPipe> findPipes(CrmConnection connection);

    List<CrmTaskType> findTaskTypes(CrmConnection connection);

    /** The bookings of the client this phone belongs to, newest first. */
    List<CrmBooking> findBookingsByPhone(CrmConnection connection, String phone);
}
