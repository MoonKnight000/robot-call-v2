package uz.murodjon.robotcallv2.inbound.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
import uz.murodjon.robotcallv2.inbound.application.dto.CreateInboundRouteRequest;
import uz.murodjon.robotcallv2.inbound.application.dto.InboundRouteFilter;
import uz.murodjon.robotcallv2.inbound.application.dto.UpdateInboundRouteRequest;
import uz.murodjon.robotcallv2.inbound.application.mapper.InboundRouteMapper;
import uz.murodjon.robotcallv2.inbound.application.port.output.InboundRouteRepository;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;
import uz.murodjon.robotcallv2.inbound.domain.enums.InboundAfterHoursAction;
import uz.murodjon.robotcallv2.inbound.domain.enums.InboundFailoverAction;
import uz.murodjon.robotcallv2.inbound.domain.enums.InboundRouteType;
import uz.murodjon.robotcallv2.inbound.domain.enums.QueueStrategy;
import uz.murodjon.robotcallv2.inbound.infrastructure.persistence.entity.InboundRouteEntity;
import uz.murodjon.robotcallv2.inbound.infrastructure.persistence.repository.InboundRouteJpaRepository;
import uz.murodjon.robotcallv2.scenario.infrastructure.persistence.entity.ScenarioEntity;
import uz.murodjon.robotcallv2.scenario.infrastructure.persistence.repository.ScenarioJpaRepository;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.time.Instant;
import java.util.List;

@Component
public class InboundRouteRepositoryAdapter implements InboundRouteRepository {

    private final InboundRouteJpaRepository jpa;
    private final ScenarioJpaRepository scenarioJpa;
    private final CurrentCompany company;
    private final InboundRouteMapper mapper;

    public InboundRouteRepositoryAdapter(InboundRouteJpaRepository jpa,
                                         ScenarioJpaRepository scenarioJpa,
                                         CurrentCompany company,
                                         InboundRouteMapper mapper) {
        this.jpa = jpa;
        this.scenarioJpa = scenarioJpa;
        this.company = company;
        this.mapper = mapper;
    }

    @Override
    public long create(CreateInboundRouteRequest request) {
        ScenarioEntity scenario = null;
        if (request.scenarioId() != null) {
            scenario = scenarioJpa.findById(request.scenarioId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.SCENARIO_NOT_FOUND, request.scenarioId()));
        }

        InboundRouteEntity entity = new InboundRouteEntity();
        entity.setCompanyId(company.id());
        entity.setDidNumber(request.didNumber());
        entity.setScenario(scenario);
        entity.setRouteType(request.routeType() != null ? request.routeType() : InboundRouteType.SCENARIO);
        entity.setTargetDestination(request.targetDestination());
        entity.setQueueStrategy(request.queueStrategy() != null ? request.queueStrategy() : QueueStrategy.RING_ALL);
        entity.setRingTimeoutSec(request.ringTimeoutSec() != null ? request.ringTimeoutSec() : 20);
        entity.setFailoverAction(request.failoverAction() != null ? request.failoverAction() : InboundFailoverAction.SCENARIO);
        entity.setFailoverDestination(request.failoverDestination());
        entity.setAfterHoursAction(request.afterHoursAction() != null ? request.afterHoursAction() : InboundAfterHoursAction.PLAY_MESSAGE_AND_HANGUP);
        entity.setAfterHoursDestination(request.afterHoursDestination());
        entity.setIvrMenuConfig(request.ivrMenuConfig());
        entity.setLanguage(request.language() != null && !request.language().isBlank() ? request.language() : "uz-UZ");
        entity.setBusinessHoursStart(request.businessHoursStart());
        entity.setBusinessHoursEnd(request.businessHoursEnd());
        entity.setFallbackMessage(request.fallbackMessage());
        entity.setEnabled(true);
        entity.setCreatedAt(Instant.now());
        return jpa.save(entity).getId();
    }

    @Override
    public void update(long id, UpdateInboundRouteRequest request) {
        InboundRouteEntity entity = jpa.findByIdAndCompanyId(id, company.id())
                .orElseThrow(() -> new NotFoundException(ErrorCode.INBOUND_ROUTE_NOT_FOUND, id));

        ScenarioEntity scenario = null;
        if (request.scenarioId() != null) {
            scenario = scenarioJpa.findById(request.scenarioId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.SCENARIO_NOT_FOUND, request.scenarioId()));
        }

        entity.setDidNumber(request.didNumber());
        entity.setScenario(scenario);
        if (request.routeType() != null) entity.setRouteType(request.routeType());
        entity.setTargetDestination(request.targetDestination());
        if (request.queueStrategy() != null) entity.setQueueStrategy(request.queueStrategy());
        if (request.ringTimeoutSec() != null) entity.setRingTimeoutSec(request.ringTimeoutSec());
        if (request.failoverAction() != null) entity.setFailoverAction(request.failoverAction());
        entity.setFailoverDestination(request.failoverDestination());
        if (request.afterHoursAction() != null) entity.setAfterHoursAction(request.afterHoursAction());
        entity.setAfterHoursDestination(request.afterHoursDestination());
        entity.setIvrMenuConfig(request.ivrMenuConfig());
        if (request.language() != null && !request.language().isBlank()) entity.setLanguage(request.language());
        entity.setBusinessHoursStart(request.businessHoursStart());
        entity.setBusinessHoursEnd(request.businessHoursEnd());
        entity.setFallbackMessage(request.fallbackMessage());
        entity.setEnabled(request.enabled());
        jpa.save(entity);
    }

    @Override
    public void disable(long id) {
        InboundRouteEntity entity = jpa.findByIdAndCompanyId(id, company.id())
                .orElseThrow(() -> new NotFoundException(ErrorCode.INBOUND_ROUTE_NOT_FOUND, id));
        entity.setEnabled(false);
        jpa.save(entity);
    }

    @Override
    public InboundRoute find(long id) {
        return jpa.findByIdAndCompanyId(id, company.id()).map(mapper::entityToDomain).orElse(null);
    }

    @Override
    public List<InboundRoute> findAll(InboundRouteFilter filter) {
        return jpa.findByCompanyId(company.id(), filter.pageable()).stream()
                .map(mapper::entityToDomain)
                .toList();
    }

    @Override
    public long count(InboundRouteFilter filter) {
        return jpa.countByCompanyId(company.id());
    }

    @Override
    public boolean existsEnabledByDid(String didNumber) {
        return jpa.existsByDidNumberAndEnabledTrue(didNumber);
    }

    @Override
    public boolean existsEnabledByDidExcluding(String didNumber, long id) {
        return jpa.existsByDidNumberAndEnabledTrueAndIdNot(didNumber, id);
    }

    @Override
    public InboundRoute resolveByDid(String didNumber) {
        return jpa.findByDidNumberAndEnabledTrue(didNumber).map(mapper::entityToDomain).orElse(null);
    }
}
