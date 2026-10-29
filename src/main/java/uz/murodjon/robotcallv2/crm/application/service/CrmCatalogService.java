package uz.murodjon.robotcallv2.crm.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmCatalogUseCase;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmUseCase;
import uz.murodjon.robotcallv2.crm.application.port.output.UysotCatalogPort;
import uz.murodjon.robotcallv2.crm.domain.entity.*;
import uz.murodjon.robotcallv2.crm.infrastructure.config.UysotCatalogProperties;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Uysot reads with a short per-company cache.
 *
 * <p>Complexes, employees and pipelines change rarely and are asked for on every call that
 * offers the tools, so they are kept for {@link UysotCatalogProperties#directoryTtl()}. A flat
 * search is kept only {@link UysotCatalogProperties#flatTtl()}: a flat sold an hour ago must
 * not be offered. Contracts and schedules are never cached — a payment made this morning
 * changes the figure the agent states.
 */
@Service
public class CrmCatalogService implements CrmCatalogUseCase {

    /** Bounds the cache: one entry per company and distinct search, cleared wholesale past this. */
    private static final int MAX_CACHE_ENTRIES = 5_000;

    private final CrmConnectionResolver connectionResolver;
    private final UysotCatalogPort catalogPort;
    private final UysotCatalogProperties catalogProperties;
    private final Clock clock;
    private final CrmUseCase crmUseCase;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public CrmCatalogService(CrmConnectionResolver connectionResolver, UysotCatalogPort catalogPort,
                             UysotCatalogProperties catalogProperties, Clock clock, CrmUseCase crmUseCase) {
        this.connectionResolver = connectionResolver;
        this.catalogPort = catalogPort;
        this.catalogProperties = catalogProperties;
        this.clock = clock;
        this.crmUseCase = crmUseCase;
    }

    @Override
    public boolean isUysotConnected(long companyId) {
        CrmConnection connection = connectionResolver.findConnection(companyId);
        return connection != null && connection.provider() == CrmProvider.UYSOT;
    }

    @Override
    public List<CrmHouse> findHouses(long companyId) {
        return cached("houses:" + companyId, catalogProperties.directoryTtl(),
                () -> catalogPort.findHouses(requireUysot(companyId)));
    }

    @Override
    public List<CrmFlat> searchFlats(long companyId, FlatSearchFilter filter) {
        return cached("flats:" + companyId + ":" + filter, catalogProperties.flatTtl(),
                () -> catalogPort.searchFlats(requireUysot(companyId), filter));
    }

    @Override
    public Optional<CrmContract> findContractByNumber(long companyId, String contractNumber) {
        if (contractNumber == null || contractNumber.isBlank()) {
            return Optional.empty();
        }
        return catalogPort.findContractByNumber(requireUysot(companyId), contractNumber);
    }

    @Override
    public List<CrmContract> findActiveContractsByPhone(long companyId, String phone) {
        if (phone == null || phone.isBlank()) {
            return List.of();
        }
        return catalogPort.findActiveContractsByPhone(requireUysot(companyId), phone);
    }

    @Override
    public List<CrmPaymentDue> findPaymentSchedule(long companyId, long contractId) {
        return catalogPort.findPaymentSchedule(requireUysot(companyId), contractId);
    }

    @Override
    public List<CrmEmployee> findEmployees(long companyId) {
        return cached("employees:" + companyId, catalogProperties.directoryTtl(),
                () -> catalogPort.findEmployees(requireUysot(companyId)));
    }

    @Override
    public Optional<CrmEmployee> findEmployeeById(long companyId, long employeeId) {
        return findEmployees(companyId).stream().filter(employee -> employee.id() == employeeId).findFirst();
    }

    @Override
    public List<CrmPipe> findPipes(long companyId) {
        return cached("pipes:" + companyId, catalogProperties.directoryTtl(),
                () -> catalogPort.findPipes(requireUysot(companyId)));
    }

    @Override
    public List<CrmTaskType> findTaskTypes(long companyId) {
        return cached("taskTypes:" + companyId, catalogProperties.directoryTtl(),
                () -> catalogPort.findTaskTypes(requireUysot(companyId)));
    }

    @Override
    public CrmClientProfile findClientProfile(long companyId, String phone) {
        String normalized = PhoneNumbers.require(phone);
        CrmConnection connection = requireUysot(companyId);
        return new CrmClientProfile(normalized, crmUseCase.findByPhone(companyId, normalized),
                catalogPort.findActiveContractsByPhone(connection, normalized));
    }

    private CrmConnection requireUysot(long companyId) {
        CrmConnection connection = connectionResolver.findConnection(companyId);
        if (connection == null || connection.provider() != CrmProvider.UYSOT) {
            throw new ConflictException(ErrorCode.CRM_NOT_CONNECTED);
        }
        return connection;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> cached(String key, Duration ttl, Supplier<List<T>> loader) {
        Instant now = clock.instant();
        Cached hit = cache.get(key);
        if (hit != null && hit.expiresAt().isAfter(now)) {
            return (List<T>) hit.value();
        }
        List<T> value = List.copyOf(loader.get());
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear();
        }
        cache.put(key, new Cached(value, now.plus(ttl)));
        return value;
    }

    private record Cached(List<?> value, Instant expiresAt) {
    }
}
