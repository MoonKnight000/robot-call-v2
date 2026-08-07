package uz.murodjon.uysotvoice.inbound.service;

import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.inbound.dto.CreateInboundRouteRequest;
import uz.murodjon.uysotvoice.inbound.dto.InboundRouteFilter;
import uz.murodjon.uysotvoice.inbound.dto.InboundRoute;
import uz.murodjon.uysotvoice.inbound.dto.InboundRouteRow;
import uz.murodjon.uysotvoice.inbound.dto.UpdateInboundRouteRequest;
import uz.murodjon.uysotvoice.inbound.repository.InboundRouteRepository;
import uz.murodjon.uysotvoice.report.dto.InboundRouteStats;
import uz.murodjon.uysotvoice.report.repository.ReportRepository;
import uz.murodjon.uysotvoice.scenario.service.ScenarioService;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.exception.ConflictException;
import uz.murodjon.uysotvoice.shared.exception.ErrorCode;
import uz.murodjon.uysotvoice.shared.exception.NotFoundException;
import uz.murodjon.uysotvoice.shared.util.PhoneNumbers;

import java.util.List;

/**
 * Inbound DID routing CRUD (ROADMAP C.1): a dialled number resolves to the scenario
 * (and language) a call runs. {@link #resolveByDid} is the runtime hot path {@code
 * AriService} calls at {@code StasisStart} — it must never throw, only the admin CRUD
 * methods below it do.
 */
@Service
public class InboundRouteService {

    private final InboundRouteRepository routes;
    private final ScenarioService scenarios;
    private final ReportRepository reports;
    private final AuditService audit;

    public InboundRouteService(InboundRouteRepository routes, ScenarioService scenarios,
                               ReportRepository reports, AuditService audit) {
        this.routes = routes;
        this.scenarios = scenarios;
        this.reports = reports;
        this.audit = audit;
    }

    public InboundRouteRow create(CreateInboundRouteRequest r) {
        String did = PhoneNumbers.require(r.didNumber());
        // 404s if the scenario is unknown or belongs to another company.
        scenarios.requireScenario(r.scenarioId());
        if (routes.existsEnabledByDid(did)) {
            throw new ConflictException(ErrorCode.INBOUND_ROUTE_DID_EXISTS, did);
        }
        long id = routes.create(did, r.scenarioId(), r.language(), r.businessHoursStart(), r.businessHoursEnd(),
                r.fallbackMessage());
        audit.record("INBOUND_ROUTE_CREATE", "inbound_route", String.valueOf(id), did);
        return routeRow(id);
    }

    public InboundRouteRow update(long id, UpdateInboundRouteRequest r) {
        requireRoute(id);
        String did = PhoneNumbers.require(r.didNumber());
        scenarios.requireScenario(r.scenarioId());
        if (r.enabled() && routes.existsEnabledByDidExcluding(did, id)) {
            throw new ConflictException(ErrorCode.INBOUND_ROUTE_DID_EXISTS, did);
        }
        routes.update(id, did, r.scenarioId(), r.language(), r.businessHoursStart(), r.businessHoursEnd(),
                r.fallbackMessage(), r.enabled());
        audit.record("INBOUND_ROUTE_UPDATE", "inbound_route", String.valueOf(id), did);
        return routeRow(id);
    }

    /** Soft-delete (mirrors campaign archive) — the DID stops matching, history is kept. */
    public InboundRouteRow disable(long id) {
        requireRoute(id);
        routes.disable(id);
        audit.record("INBOUND_ROUTE_DISABLE", "inbound_route", String.valueOf(id), null);
        return routeRow(id);
    }

    /**
     * API-facing list (backend-uchun-talablar.md §3) — enriches each row with
     * {@code scenarioName} via a batched lookup rather than one query per row.
     */
    public PageableData<InboundRouteRow> list(InboundRouteFilter filter) {
        List<InboundRoute> rows = routes.findAll(filter);
        long total = routes.count(filter);
        java.util.Map<Long, String> scenarioNames = scenarios.scenarioNamesByIds(
                rows.stream().map(InboundRoute::scenarioId).collect(java.util.stream.Collectors.toSet()));
        List<InboundRouteRow> enriched = rows.stream()
                .map(row -> InboundRouteRow.of(row, scenarioNames.get(row.scenarioId())))
                .toList();
        return PageableData.of(enriched, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    /** As {@link InboundRouteRepository#find}, for internal callers — missing is a 404, not a null. */
    public InboundRoute requireRoute(long id) {
        InboundRoute row = routes.find(id);
        if (row == null) {
            throw new NotFoundException(ErrorCode.INBOUND_ROUTE_NOT_FOUND, id);
        }
        return row;
    }

    /** As {@link #requireRoute}, enriched with {@code scenarioName} for the REST API. */
    public InboundRouteRow routeRow(long id) {
        InboundRoute route = requireRoute(id);
        String scenarioName = scenarios.scenarioNamesByIds(List.of(route.scenarioId())).get(route.scenarioId());
        return InboundRouteRow.of(route, scenarioName);
    }

    /**
     * The route for {@code did}, or {@code null} if none is enabled for it — called from
     * {@code AriService} at {@code StasisStart}, on the call thread, so this never throws.
     */
    public InboundRoute resolveByDid(String did) {
        return routes.resolveByDid(did);
    }

    /** "Shu raqamga tushgan qo'ng'iroqlar statistikasi" (§10.9 drawer). */
    public InboundRouteStats stats(long id) {
        requireRoute(id); // 404s before touching call_attempt if the route itself is missing
        return reports.inboundRouteStats(id);
    }
}
