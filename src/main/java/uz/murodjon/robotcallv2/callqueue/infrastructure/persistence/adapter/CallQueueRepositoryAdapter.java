package uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.callqueue.application.port.output.CallQueueRepository;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueueFilter;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueueMember;
import uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.entity.CallQueueEntity;
import uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.entity.CallQueueMemberSkillEntity;
import uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.mapper.CallQueueEntityMapper;
import uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.repository.CallQueueJpaRepository;
import uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.repository.CallQueueMemberSkillJpaRepository;

import java.time.Instant;
import java.util.*;

@Component
public class CallQueueRepositoryAdapter implements CallQueueRepository {

    private final CallQueueJpaRepository jpaRepository;
    private final CallQueueMemberSkillJpaRepository skillJpaRepository;
    private final CallQueueEntityMapper mapper;

    public CallQueueRepositoryAdapter(CallQueueJpaRepository jpaRepository,
                                      CallQueueMemberSkillJpaRepository skillJpaRepository,
                                      CallQueueEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.skillJpaRepository = skillJpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public long create(long companyId, CallQueue queue) {
        CallQueueEntity entity = new CallQueueEntity();
        entity.setCompanyId(companyId);
        entity.setCreatedAt(Instant.now());
        copy(queue, entity);
        long queueId = jpaRepository.save(entity).getId();
        saveSkills(queueId, queue.members());
        return queueId;
    }

    @Override
    @Transactional
    public void update(long companyId, long id, CallQueue queue) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            copy(queue, entity);
            jpaRepository.save(entity);
            skillJpaRepository.deleteByIdQueueId(id);
            saveSkills(id, queue.members());
        });
    }

    @Override
    @Transactional
    public void delete(long companyId, long id) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            skillJpaRepository.deleteByIdQueueId(id);
            jpaRepository.delete(entity);
        });
    }

    @Override
    public CallQueue find(long companyId, long id) {
        return jpaRepository.findByIdAndCompanyId(id, companyId)
                .map(entity -> {
                    List<CallQueueMemberSkillEntity> skills = skillJpaRepository.findByIdQueueId(id);
                    Map<Long, Set<String>> userSkills = groupSkillsByUser(skills);
                    return mapper.toCallQueue(entity, userSkills);
                })
                .orElse(null);
    }

    @Override
    public List<CallQueue> findAll(long companyId, CallQueueFilter filter) {
        List<CallQueueEntity> entities = jpaRepository.findByCompanyId(companyId, filter.pageable());
        return mapEntities(entities);
    }

    @Override
    public long count(long companyId) {
        return jpaRepository.countByCompanyId(companyId);
    }

    @Override
    public List<CallQueue> findEnabledByCompanyId(long companyId) {
        List<CallQueueEntity> entities = jpaRepository.findByCompanyIdAndEnabledTrue(companyId);
        return mapEntities(entities);
    }

    private List<CallQueue> mapEntities(List<CallQueueEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        List<Long> queueIds = entities.stream().map(CallQueueEntity::getId).toList();
        List<CallQueueMemberSkillEntity> allSkills = skillJpaRepository.findByIdQueueIdIn(queueIds);
        Map<Long, Map<Long, Set<String>>> queueUserSkills = groupSkillsByQueueAndUser(allSkills);
        return entities.stream()
                .map(e -> mapper.toCallQueue(e, queueUserSkills.getOrDefault(e.getId(), Map.of())))
                .toList();
    }

    private static Map<Long, Map<Long, Set<String>>> groupSkillsByQueueAndUser(List<CallQueueMemberSkillEntity> skills) {
        Map<Long, Map<Long, Set<String>>> result = new HashMap<>();
        for (CallQueueMemberSkillEntity entity : skills) {
            result.computeIfAbsent(entity.getQueueId(), k -> new HashMap<>())
                    .computeIfAbsent(entity.getUserId(), k -> new LinkedHashSet<>())
                    .add(entity.getSkill());
        }
        return result;
    }

    private static Map<Long, Set<String>> groupSkillsByUser(List<CallQueueMemberSkillEntity> skills) {
        Map<Long, Set<String>> result = new HashMap<>();
        for (CallQueueMemberSkillEntity entity : skills) {
            result.computeIfAbsent(entity.getUserId(), k -> new LinkedHashSet<>())
                    .add(entity.getSkill());
        }
        return result;
    }

    private void saveSkills(long queueId, List<CallQueueMember> members) {
        if (members == null || members.isEmpty()) {
            return;
        }
        List<CallQueueMemberSkillEntity> skillEntities = new ArrayList<>();
        for (CallQueueMember member : members) {
            if (member.skills() != null) {
                for (String skill : member.skills()) {
                    if (skill != null && !skill.isBlank()) {
                        skillEntities.add(new CallQueueMemberSkillEntity(queueId, member.userId(), skill.trim()));
                    }
                }
            }
        }
        if (!skillEntities.isEmpty()) {
            skillJpaRepository.saveAll(skillEntities);
        }
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
