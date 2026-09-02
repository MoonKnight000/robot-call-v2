package uz.murodjon.robotcallv2.inbound.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.inbound.application.dto.CreateInboundRouteRequest;
import uz.murodjon.robotcallv2.inbound.application.dto.InboundRouteFilter;
import uz.murodjon.robotcallv2.inbound.application.dto.InboundRouteRow;
import uz.murodjon.robotcallv2.inbound.application.dto.UpdateInboundRouteRequest;
import uz.murodjon.robotcallv2.inbound.application.port.input.InboundRouteUseCase;
import uz.murodjon.robotcallv2.inbound.application.port.output.InboundRouteRepository;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;
import uz.murodjon.robotcallv2.inbound.domain.service.InboundRouteValidator;
import uz.murodjon.robotcallv2.report.application.port.output.ReportRepository;
import uz.murodjon.robotcallv2.report.domain.entity.InboundRouteStats;
import uz.murodjon.robotcallv2.scenario.application.port.input.ScenarioUseCase;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Inbound DID routing CRUD and Virtual ATS management (ROADMAP C.1).
 */
@Service
public class InboundRouteService implements InboundRouteUseCase {

    private final InboundRouteRepository routes;
    private final ScenarioUseCase scenarios;
    private final ReportRepository reports;
    private final AuditService audit;

    public InboundRouteService(InboundRouteRepository routes, ScenarioUseCase scenarios,
                               ReportRepository reports, AuditService audit) {
        this.routes = routes;
        this.scenarios = scenarios;
        this.reports = reports;
        this.audit = audit;
    }

    @Override
    public InboundRouteRow create(CreateInboundRouteRequest r) {
        InboundRouteValidator.validateBusinessHours(r.businessHoursStart(), r.businessHoursEnd());
        String did = PhoneNumbers.require(r.didNumber());
        if (r.scenarioId() != null) {
            scenarios.requireScenario(r.scenarioId());
        }
        if (routes.existsEnabledByDid(did)) {
            throw new ConflictException(ErrorCode.INBOUND_ROUTE_DID_EXISTS, did);
        }
        long id = routes.create(r);
        audit.record("INBOUND_ROUTE_CREATE", "inbound_route", String.valueOf(id), did);
        return routeRow(id);
    }

    @Override
    public InboundRouteRow update(long id, UpdateInboundRouteRequest r) {
        InboundRouteValidator.validateBusinessHours(r.businessHoursStart(), r.businessHoursEnd());
        requireRoute(id);
        String did = PhoneNumbers.require(r.didNumber());
        if (r.scenarioId() != null) {
            scenarios.requireScenario(r.scenarioId());
        }
        if (r.enabled() && routes.existsEnabledByDidExcluding(did, id)) {
            throw new ConflictException(ErrorCode.INBOUND_ROUTE_DID_EXISTS, did);
        }
        routes.update(id, r);
        audit.record("INBOUND_ROUTE_UPDATE", "inbound_route", String.valueOf(id), did);
        return routeRow(id);
    }

    @Override
    public InboundRouteRow disable(long id) {
        requireRoute(id);
        routes.disable(id);
        audit.record("INBOUND_ROUTE_DISABLE", "inbound_route", String.valueOf(id), null);
        return routeRow(id);
    }

    @Override
    public PageableData<InboundRouteRow> list(InboundRouteFilter filter) {
        List<InboundRoute> rows = routes.findAll(filter);
        long total = routes.count(filter);
        Set<Long> scenarioIds = rows.stream()
                .map(InboundRoute::scenarioId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        java.util.Map<Long, String> scenarioNames = scenarioIds.isEmpty()
                ? java.util.Map.of()
                : scenarios.scenarioNamesByIds(scenarioIds);

        List<InboundRouteRow> enriched = rows.stream()
                .map(row -> InboundRouteRow.of(row, row.scenarioId() != null ? scenarioNames.get(row.scenarioId()) : null))
                .toList();
        return PageableData.of(enriched, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    @Override
    public InboundRoute requireRoute(long id) {
        InboundRoute row = routes.find(id);
        if (row == null) {
            throw new NotFoundException(ErrorCode.INBOUND_ROUTE_NOT_FOUND, id);
        }
        return row;
    }

    @Override
    public InboundRouteRow routeRow(long id) {
        InboundRoute route = requireRoute(id);
        String scenarioName = null;
        if (route.scenarioId() != null) {
            scenarioName = scenarios.scenarioNamesByIds(List.of(route.scenarioId())).get(route.scenarioId());
        }
        return InboundRouteRow.of(route, scenarioName);
    }

    @Override
    public InboundRoute resolveByDid(String did) {
        return routes.resolveByDid(did);
    }

    @Override
    public InboundRouteStats stats(long id) {
        requireRoute(id);
        return reports.inboundRouteStats(id);
    }
}
