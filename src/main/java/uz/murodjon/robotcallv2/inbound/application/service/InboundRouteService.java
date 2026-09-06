package uz.murodjon.robotcallv2.inbound.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.inbound.application.dto.CreateInboundRouteRequest;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRouteFilter;
import uz.murodjon.robotcallv2.inbound.application.dto.InboundRouteRow;
import uz.murodjon.robotcallv2.inbound.application.dto.UpdateInboundRouteRequest;
import uz.murodjon.robotcallv2.inbound.application.port.input.InboundRouteUseCase;
import uz.murodjon.robotcallv2.inbound.application.port.output.InboundRouteRepository;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;
import uz.murodjon.robotcallv2.inbound.domain.service.InboundRouteValidator;
import uz.murodjon.robotcallv2.report.application.port.output.ReportRepository;
import uz.murodjon.robotcallv2.report.domain.entity.InboundRouteStats;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
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
    private final AiAgentUseCase aiAgents;
    private final ReportRepository reports;
    private final AuditService audit;

    public InboundRouteService(InboundRouteRepository routes, AiAgentUseCase aiAgents,
                               ReportRepository reports, AuditService audit) {
        this.routes = routes;
        this.aiAgents = aiAgents;
        this.reports = reports;
        this.audit = audit;
    }

    @Override
    public InboundRouteRow create(long companyId, CreateInboundRouteRequest r) {
        InboundRouteValidator.validateBusinessHours(r.businessHoursStart(), r.businessHoursEnd());
        String did = PhoneNumbers.require(r.didNumber());
        if (r.aiAgentId() != null) {
            aiAgents.requireAgent(companyId, r.aiAgentId());
        }
        if (routes.existsEnabledByDid(did)) {
            throw new ConflictException(ErrorCode.INBOUND_ROUTE_DID_EXISTS, did);
        }
        long id = routes.create(companyId, r);
        audit.record(companyId, "INBOUND_ROUTE_CREATE", "inbound_route", String.valueOf(id), did);
        return routeRow(companyId, id);
    }

    @Override
    public InboundRouteRow update(long companyId, long id, UpdateInboundRouteRequest r) {
        InboundRouteValidator.validateBusinessHours(r.businessHoursStart(), r.businessHoursEnd());
        requireRoute(companyId, id);
        String did = PhoneNumbers.require(r.didNumber());
        if (r.aiAgentId() != null) {
            aiAgents.requireAgent(companyId, r.aiAgentId());
        }
        if (r.enabled() && routes.existsEnabledByDidExcluding(did, id)) {
            throw new ConflictException(ErrorCode.INBOUND_ROUTE_DID_EXISTS, did);
        }
        routes.update(companyId, id, r);
        audit.record(companyId, "INBOUND_ROUTE_UPDATE", "inbound_route", String.valueOf(id), did);
        return routeRow(companyId, id);
    }

    @Override
    public InboundRouteRow disable(long companyId, long id) {
        requireRoute(companyId, id);
        routes.disable(companyId, id);
        audit.record(companyId, "INBOUND_ROUTE_DISABLE", "inbound_route", String.valueOf(id), null);
        return routeRow(companyId, id);
    }

    @Override
    public PageableData<InboundRouteRow> list(long companyId, InboundRouteFilter filter) {
        List<InboundRoute> rows = routes.findAll(companyId, filter);
        long total = routes.count(companyId, filter);
        Set<Long> agentIds = rows.stream()
                .map(InboundRoute::aiAgentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        java.util.Map<Long, String> agentNames = agentIds.isEmpty()
                ? java.util.Map.of()
                : aiAgents.findNamesByIds(agentIds);

        List<InboundRouteRow> enriched = rows.stream()
                .map(row -> InboundRouteRow.of(row, row.aiAgentId() != null ? agentNames.get(row.aiAgentId()) : null))
                .toList();
        return PageableData.of(enriched, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    @Override
    public InboundRoute requireRoute(long companyId, long id) {
        InboundRoute row = routes.find(companyId, id);
        if (row == null) {
            throw new NotFoundException(ErrorCode.INBOUND_ROUTE_NOT_FOUND, id);
        }
        return row;
    }

    @Override
    public InboundRouteRow routeRow(long companyId, long id) {
        InboundRoute route = requireRoute(companyId, id);
        String agentName = null;
        if (route.aiAgentId() != null) {
            agentName = aiAgents.findNamesByIds(List.of(route.aiAgentId())).get(route.aiAgentId());
        }
        return InboundRouteRow.of(route, agentName);
    }

    @Override
    public InboundRoute resolveByDid(String did) {
        return routes.resolveByDid(did);
    }

    @Override
    public InboundRouteStats stats(long companyId, long id) {
        requireRoute(companyId, id);
        return reports.inboundRouteStats(companyId, id);
    }
}
