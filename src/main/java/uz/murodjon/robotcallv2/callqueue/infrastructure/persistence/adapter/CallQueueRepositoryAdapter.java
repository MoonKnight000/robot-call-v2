package uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.callqueue.application.port.output.CallQueueRepository;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueueFilter;
import uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.entity.CallQueueEntity;
import uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.mapper.CallQueueEntityMapper;
import uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.repository.CallQueueJpaRepository;

import java.time.Instant;
import java.util.List;

@Component
public class CallQueueRepositoryAdapter implements CallQueueRepository {

    private final CallQueueJpaRepository jpaRepository;
    private final CallQueueEntityMapper mapper;

    public CallQueueRepositoryAdapter(CallQueueJpaRepository jpaRepository, CallQueueEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public long create(long companyId, CallQueue queue) {
        CallQueueEntity entity = new CallQueueEntity();
        entity.setCompanyId(companyId);
        entity.setCreatedAt(Instant.now());
        copy(queue, entity);
        return jpaRepository.save(entity).getId();
    }

    @Override
    @Transactional
    public void update(long companyId, long id, CallQueue queue) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            copy(queue, entity);
            jpaRepository.save(entity);
        });
    }

    @Override
    @Transactional
    public void delete(long companyId, long id) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(jpaRepository::delete);
    }

    @Override
    public CallQueue find(long companyId, long id) {
        return jpaRepository.findByIdAndCompanyId(id, companyId).map(mapper::toCallQueue).orElse(null);
    }

    @Override
    public List<CallQueue> findAll(long companyId, CallQueueFilter filter) {
        return jpaRepository.findByCompanyId(companyId, filter.pageable()).stream().map(mapper::toCallQueue).toList();
    }

    @Override
    public long count(long companyId) {
        return jpaRepository.countByCompanyId(companyId);
    }

    @Override
    public List<CallQueue> findEnabledByCompanyId(long companyId) {
        return jpaRepository.findByCompanyIdAndEnabledTrue(companyId).stream().map(mapper::toCallQueue).toList();
    }

    private void copy(CallQueue q, CallQueueEntity e) {
        e.setName(q.name());
        e.setStrategy(q.strategy());
        e.setRingTimeoutSec(q.ringTimeoutSec());
        e.setWrapUpSec(q.wrapUpSec());
        e.setMaxWaitSec(q.maxWaitSec());
        e.setMaxLength(q.maxLength());
        e.setAnnouncePositionEverySec(q.announcePositionEverySec());
        e.setAnnounceWaitTime(q.announceWaitTime());
        e.setPeriodicAnnounceText(q.periodicAnnounceText());
        e.setPeriodicAnnounceEverySec(q.periodicAnnounceEverySec());
        e.setHoldMusicFileId(q.holdMusicFileId());
        e.setCallbackOfferAfterSec(q.callbackOfferAfterSec());
        e.setCallbackCampaignId(q.callbackCampaignId());
        e.setOverflowAction(q.overflowAction());
        e.setOverflowTarget(q.overflowTarget());
        e.setServiceLevelSec(q.serviceLevelSec());
        e.setStickySeconds(q.stickySeconds());
        e.setPostCallSurvey(q.postCallSurvey());
        e.setWaitingAgentId(q.waitingAgentId());
        e.setAutoCallback(q.autoCallback());
        e.setEnabled(q.enabled());
        e.setMembers(mapper.toCallQueueMemberEntities(q.members()));
    }
}
