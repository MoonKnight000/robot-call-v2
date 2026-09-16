package uz.murodjon.robotcallv2.inbound.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentEntity;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository.AiAgentJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.inbound.infrastructure.persistence.mapper.InboundRouteEntityMapper;
import uz.murodjon.robotcallv2.inbound.application.port.output.InboundRouteRepository;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRouteFilter;
import uz.murodjon.robotcallv2.inbound.domain.enums.InboundAfterHoursAction;
import uz.murodjon.robotcallv2.inbound.domain.enums.InboundFailoverAction;
import uz.murodjon.robotcallv2.inbound.domain.enums.InboundRouteType;
import uz.murodjon.robotcallv2.inbound.domain.enums.QueueStrategy;
import uz.murodjon.robotcallv2.inbound.infrastructure.persistence.entity.InboundRouteEntity;
import uz.murodjon.robotcallv2.inbound.infrastructure.persistence.repository.InboundRouteJpaRepository;

import java.time.Instant;
import java.util.List;

@Component
public class InboundRouteRepositoryAdapter implements InboundRouteRepository {

    private final InboundRouteJpaRepository jpaRepository;
    private final InboundRouteEntityMapper mapper;
    private final CompanyJpaRepository companyJpaRepository;
    private final AiAgentJpaRepository aiAgentJpaRepository;

    public InboundRouteRepositoryAdapter(InboundRouteJpaRepository jpaRepository,
                                         InboundRouteEntityMapper mapper,
                                         CompanyJpaRepository companyJpaRepository,
                                         AiAgentJpaRepository aiAgentJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.companyJpaRepository = companyJpaRepository;
        this.aiAgentJpaRepository = aiAgentJpaRepository;
    }

    @Override
    public long create(long companyId, InboundRoute route) {
        // The agent is validated by InboundRouteService before it gets here.

        InboundRouteEntity entity = new InboundRouteEntity();
        entity.setCompany(companyJpaRepository.getReferenceById(companyId));
        entity.setDidNumber(route.didNumber());
        entity.setAiAgent(aiAgentReference(route.aiAgentId()));
        entity.setRouteType(route.routeType() != null ? route.routeType() : InboundRouteType.SCENARIO);
        entity.setTargetDestination(route.targetDestination());
        entity.setQueueStrategy(route.queueStrategy() != null ? route.queueStrategy() : QueueStrategy.RING_ALL);
        entity.setRingTimeoutSec(route.ringTimeoutSec() > 0 ? route.ringTimeoutSec() : 20);
        entity.setFailoverAction(route.failoverAction() != null ? route.failoverAction() : InboundFailoverAction.SCENARIO);
        entity.setFailoverDestination(route.failoverDestination());
        entity.setAfterHoursAction(route.afterHoursAction() != null ? route.afterHoursAction() : InboundAfterHoursAction.PLAY_MESSAGE_AND_HANGUP);
        entity.setAfterHoursDestination(route.afterHoursDestination());
        entity.setIvrMenuConfig(route.ivrMenuConfig());
        entity.setBusinessHoursStart(route.businessHoursStart());
        entity.setBusinessHoursEnd(route.businessHoursEnd());
        entity.setFallbackMessage(route.fallbackMessage());
        entity.setCallbackCampaignId(route.callbackCampaignId());
        entity.setScheduleId(route.scheduleId());
        entity.setCallFlowId(route.callFlowId());
        entity.setEnabled(true);
        entity.setCreatedAt(Instant.now());
        return jpaRepository.save(entity).getId();
    }

    @Override
    public void update(long companyId, long id, InboundRoute route) {
        // InboundRouteService checked the route exists; a row gone since is left alone.
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            entity.setDidNumber(route.didNumber());
            entity.setAiAgent(aiAgentReference(route.aiAgentId()));
            if (route.routeType() != null) entity.setRouteType(route.routeType());
            entity.setTargetDestination(route.targetDestination());
            if (route.queueStrategy() != null) entity.setQueueStrategy(route.queueStrategy());
            if (route.ringTimeoutSec() > 0) entity.setRingTimeoutSec(route.ringTimeoutSec());
            if (route.failoverAction() != null) entity.setFailoverAction(route.failoverAction());
            entity.setFailoverDestination(route.failoverDestination());
            if (route.afterHoursAction() != null) entity.setAfterHoursAction(route.afterHoursAction());
            entity.setAfterHoursDestination(route.afterHoursDestination());
            entity.setIvrMenuConfig(route.ivrMenuConfig());
            entity.setBusinessHoursStart(route.businessHoursStart());
            entity.setBusinessHoursEnd(route.businessHoursEnd());
            entity.setFallbackMessage(route.fallbackMessage());
            entity.setCallbackCampaignId(route.callbackCampaignId());
            entity.setScheduleId(route.scheduleId());
            entity.setCallFlowId(route.callFlowId());
            entity.setEnabled(route.enabled());
            jpaRepository.save(entity);
        });
    }

    @Override
    public void disable(long companyId, long id) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            entity.setEnabled(false);
            jpaRepository.save(entity);
        });
    }

    @Override
    public InboundRoute find(long companyId, long id) {
        return jpaRepository.findByIdAndCompanyId(id, companyId).map(mapper::toInboundRoute).orElse(null);
    }

    @Override
    public List<InboundRoute> findAll(long companyId, InboundRouteFilter filter) {
        return jpaRepository.findByCompanyId(companyId, filter.pageable()).stream()
                .map(mapper::toInboundRoute)
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
        return jpaRepository.findByDidNumberAndEnabledTrue(didNumber).map(mapper::toInboundRoute).orElse(null);
    }

    /** Null for a route that goes to a queue rather than an agent. */
    private AiAgentEntity aiAgentReference(Long id) {
        return id != null ? aiAgentJpaRepository.getReferenceById(id) : null;
    }
}
