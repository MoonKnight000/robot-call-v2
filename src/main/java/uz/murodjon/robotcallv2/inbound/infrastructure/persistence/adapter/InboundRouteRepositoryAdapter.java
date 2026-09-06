package uz.murodjon.robotcallv2.inbound.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.inbound.application.dto.CreateInboundRouteRequest;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRouteFilter;
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
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.time.Instant;
import java.util.List;

@Component
public class InboundRouteRepositoryAdapter implements InboundRouteRepository {

    private final InboundRouteJpaRepository jpaRepository;
    private final InboundRouteMapper mapper;

    public InboundRouteRepositoryAdapter(InboundRouteJpaRepository jpaRepository,
                                         InboundRouteMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public long create(long companyId, CreateInboundRouteRequest request) {
        // The agent is validated by InboundRouteService before it gets here.

        InboundRouteEntity entity = new InboundRouteEntity();
        entity.setCompanyId(companyId);
        entity.setDidNumber(request.didNumber());
        entity.setAiAgentId(request.aiAgentId());
        entity.setRouteType(request.routeType() != null ? request.routeType() : InboundRouteType.SCENARIO);
        entity.setTargetDestination(request.targetDestination());
        entity.setQueueStrategy(request.queueStrategy() != null ? request.queueStrategy() : QueueStrategy.RING_ALL);
        entity.setRingTimeoutSec(request.ringTimeoutSec() != null ? request.ringTimeoutSec() : 20);
        entity.setFailoverAction(request.failoverAction() != null ? request.failoverAction() : InboundFailoverAction.SCENARIO);
        entity.setFailoverDestination(request.failoverDestination());
        entity.setAfterHoursAction(request.afterHoursAction() != null ? request.afterHoursAction() : InboundAfterHoursAction.PLAY_MESSAGE_AND_HANGUP);
        entity.setAfterHoursDestination(request.afterHoursDestination());
        entity.setIvrMenuConfig(request.ivrMenuConfig());
        entity.setBusinessHoursStart(request.businessHoursStart());
        entity.setBusinessHoursEnd(request.businessHoursEnd());
        entity.setFallbackMessage(request.fallbackMessage());
        entity.setEnabled(true);
        entity.setCreatedAt(Instant.now());
        return jpaRepository.save(entity).getId();
    }

    @Override
    public void update(long companyId, long id, UpdateInboundRouteRequest request) {
        InboundRouteEntity entity = jpaRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INBOUND_ROUTE_NOT_FOUND, id));


        entity.setDidNumber(request.didNumber());
        entity.setAiAgentId(request.aiAgentId());
        if (request.routeType() != null) entity.setRouteType(request.routeType());
        entity.setTargetDestination(request.targetDestination());
        if (request.queueStrategy() != null) entity.setQueueStrategy(request.queueStrategy());
        if (request.ringTimeoutSec() != null) entity.setRingTimeoutSec(request.ringTimeoutSec());
        if (request.failoverAction() != null) entity.setFailoverAction(request.failoverAction());
        entity.setFailoverDestination(request.failoverDestination());
        if (request.afterHoursAction() != null) entity.setAfterHoursAction(request.afterHoursAction());
        entity.setAfterHoursDestination(request.afterHoursDestination());
        entity.setIvrMenuConfig(request.ivrMenuConfig());
        entity.setBusinessHoursStart(request.businessHoursStart());
        entity.setBusinessHoursEnd(request.businessHoursEnd());
        entity.setFallbackMessage(request.fallbackMessage());
        entity.setEnabled(request.enabled());
        jpaRepository.save(entity);
    }

    @Override
    public void disable(long companyId, long id) {
        InboundRouteEntity entity = jpaRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INBOUND_ROUTE_NOT_FOUND, id));
        entity.setEnabled(false);
        jpaRepository.save(entity);
    }

    @Override
    public InboundRoute find(long companyId, long id) {
        return jpaRepository.findByIdAndCompanyId(id, companyId).map(mapper::entityToDomain).orElse(null);
    }

    @Override
    public List<InboundRoute> findAll(long companyId, InboundRouteFilter filter) {
        return jpaRepository.findByCompanyId(companyId, filter.pageable()).stream()
                .map(mapper::entityToDomain)
                .toList();
    }

    @Override
    public long count(long companyId, InboundRouteFilter filter) {
        return jpaRepository.countByCompanyId(companyId);
    }

    @Override
    public boolean existsEnabledByDid(String didNumber) {
        return jpaRepository.existsByDidNumberAndEnabledTrue(didNumber);
    }

    @Override
    public boolean existsEnabledByDidExcluding(String didNumber, long id) {
        return jpaRepository.existsByDidNumberAndEnabledTrueAndIdNot(didNumber, id);
    }

    @Override
    public InboundRoute resolveByDid(String didNumber) {
        return jpaRepository.findByDidNumberAndEnabledTrue(didNumber).map(mapper::entityToDomain).orElse(null);
    }
}
