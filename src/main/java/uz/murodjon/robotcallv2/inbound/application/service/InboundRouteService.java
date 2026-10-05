package uz.murodjon.robotcallv2.inbound.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.callflow.application.port.input.CallFlowUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.inbound.application.dto.CreateInboundRouteRequest;
import uz.murodjon.robotcallv2.inbound.application.dto.InboundRouteRow;
import uz.murodjon.robotcallv2.inbound.application.dto.UpdateInboundRouteRequest;
import uz.murodjon.robotcallv2.inbound.application.mapper.InboundRouteMapper;
import uz.murodjon.robotcallv2.inbound.application.port.input.InboundRouteUseCase;
import uz.murodjon.robotcallv2.inbound.application.port.output.InboundRouteRepository;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRouteFilter;
import uz.murodjon.robotcallv2.inbound.domain.service.InboundRouteValidator;
import uz.murodjon.robotcallv2.report.application.port.input.ReportUseCase;
import uz.murodjon.robotcallv2.report.domain.entity.InboundRouteStats;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.schedule.application.port.input.BusinessScheduleUseCase;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
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

    private final InboundRouteRepository inboundRouteRepository;
    private final AiAgentUseCase aiAgentUseCase;
    private final ReportUseCase reportUseCase;
    private final AuditUseCase audit;
    /** Checks that a callback campaign is one of the company's own. */
    private final CampaignUseCase campaignUseCase;
    /** The schedule and the call flow a route names must be the company's own. */
    private final BusinessScheduleUseCase businessScheduleUseCase;
    private final CallFlowUseCase callFlowUseCase;
    private final CurrentUser currentUser;
    private final InboundRouteMapper inboundRouteMapper;

    public InboundRouteService(InboundRouteRepository inboundRouteRepository, AiAgentUseCase aiAgentUseCase,
                               ReportUseCase reportUseCase, AuditUseCase audit, CampaignUseCase campaignUseCase,
                               BusinessScheduleUseCase businessScheduleUseCase, CallFlowUseCase callFlowUseCase,
                               CurrentUser currentUser, InboundRouteMapper inboundRouteMapper) {
        this.currentUser = currentUser;
        this.inboundRouteMapper = inboundRouteMapper;
        this.inboundRouteRepository = inboundRouteRepository;
        this.aiAgentUseCase = aiAgentUseCase;
        this.reportUseCase = reportUseCase;
        this.audit = audit;
        this.campaignUseCase = campaignUseCase;
        this.businessScheduleUseCase = businessScheduleUseCase;
        this.callFlowUseCase = callFlowUseCase;
    }

    @Override
    public InboundRouteRow create(long companyId, CreateInboundRouteRequest request) {
        InboundRouteValidator.validateBusinessHours(request.businessHoursStart(), request.businessHoursEnd());
        String did = PhoneNumbers.require(request.didNumber());
        requireDidAssignable();
        if (request.aiAgentId() != null) {
            aiAgentUseCase.requireAgent(companyId, request.aiAgentId());
        }
        if (request.callbackCampaignId() != null) {
            campaignUseCase.requireCampaign(companyId, request.callbackCampaignId());
        }
        if (request.scheduleId() != null) {
            businessScheduleUseCase.requireSchedule(companyId, request.scheduleId());
        }
        if (request.callFlowId() != null) {
            callFlowUseCase.requirePublishedFlow(companyId, request.callFlowId());
        }
        if (inboundRouteRepository.existsEnabledByDid(did)) {
            throw new ConflictException(ErrorCode.INBOUND_ROUTE_DID_EXISTS, did);
        }
        long id = inboundRouteRepository.create(companyId, inboundRouteMapper.toNewInboundRoute(request));
        audit.record(companyId, "INBOUND_ROUTE_CREATE", "inbound_route", String.valueOf(id), did);
        return requireRouteRow(companyId, id);
    }

    @Override
    public InboundRouteRow update(long companyId, long id, UpdateInboundRouteRequest request) {
        InboundRouteValidator.validateBusinessHours(request.businessHoursStart(), request.businessHoursEnd());
        InboundRoute existing = requireRoute(companyId, id);
        String did = PhoneNumbers.require(request.didNumber());
        if (!did.equals(PhoneNumbers.normalize(existing.didNumber()))) {
            requireDidAssignable();
        }
        if (request.aiAgentId() != null) {
            aiAgentUseCase.requireAgent(companyId, request.aiAgentId());
        }
        if (request.callbackCampaignId() != null) {
            campaignUseCase.requireCampaign(companyId, request.callbackCampaignId());
        }
        if (request.scheduleId() != null) {
            businessScheduleUseCase.requireSchedule(companyId, request.scheduleId());
        }
        if (request.callFlowId() != null) {
            callFlowUseCase.requirePublishedFlow(companyId, request.callFlowId());
        }
        if (request.enabled() && inboundRouteRepository.existsEnabledByDidExcluding(did, id)) {
            throw new ConflictException(ErrorCode.INBOUND_ROUTE_DID_EXISTS, did);
        }
        inboundRouteRepository.update(companyId, id, inboundRouteMapper.toUpdatedInboundRoute(request));
        audit.record(companyId, "INBOUND_ROUTE_UPDATE", "inbound_route", String.valueOf(id), did);
        return requireRouteRow(companyId, id);
    }

    @Override
    public InboundRouteRow disable(long companyId, long id) {
        requireRoute(companyId, id);
        inboundRouteRepository.disable(companyId, id);
        audit.record(companyId, "INBOUND_ROUTE_DISABLE", "inbound_route", String.valueOf(id), null);
        return requireRouteRow(companyId, id);
    }

    @Override
    public PageableData<InboundRouteRow> list(long companyId, InboundRouteFilter filter) {
        List<InboundRoute> rows = inboundRouteRepository.findAll(companyId, filter);
        long total = inboundRouteRepository.count(companyId, filter);
        Set<Long> agentIds = rows.stream()
                .map(InboundRoute::aiAgentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        java.util.Map<Long, String> agentNames = agentIds.isEmpty()
                ? java.util.Map.of()
                : aiAgentUseCase.findNamesByIds(agentIds);

        List<InboundRouteRow> enriched = rows.stream()
                .map(row -> InboundRouteRow.of(row, row.aiAgentId() != null ? agentNames.get(row.aiAgentId()) : null))
                .toList();
        return PageableData.of(enriched, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    /**
     * A route's DID decides which company answers every call to that number, and nothing
     * here can tell whether a number is really this tenant's — so a tenant that claimed
     * another's number (first, or while that one's route was disabled) took over its
     * inbound calls and recordings. Numbers are the platform's to hand out; the tenant
     * keeps editing everything else on its routes.
     */
    private void requireDidAssignable() {
        if (!currentUser.hasPermission(Permission.PLATFORM_ADMIN)) {
            throw new ForbiddenException(ErrorCode.INBOUND_ROUTE_DID_PLATFORM_ONLY);
        }
    }

    @Override
    public InboundRoute requireRoute(long companyId, long id) {
        InboundRoute row = inboundRouteRepository.find(companyId, id);
        if (row == null) {
            throw new NotFoundException(ErrorCode.INBOUND_ROUTE_NOT_FOUND, id);
        }
        return row;
    }

    @Override
    public InboundRouteRow requireRouteRow(long companyId, long id) {
        InboundRoute route = requireRoute(companyId, id);
        String agentName = null;
        if (route.aiAgentId() != null) {
            agentName = aiAgentUseCase.findNamesByIds(List.of(route.aiAgentId())).get(route.aiAgentId());
        }
        return InboundRouteRow.of(route, agentName);
    }

    @Override
    public InboundRoute resolveByDid(String did) {
        return inboundRouteRepository.resolveByDid(did);
    }

    @Override
    public InboundRouteStats findStats(long companyId, long id) {
        requireRoute(companyId, id);
        return reportUseCase.findInboundRouteStats(companyId, id);
    }
}
