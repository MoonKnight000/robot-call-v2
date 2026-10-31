package uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueueMember;
import uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.entity.CallQueueEntity;
import uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.entity.CallQueueMemberEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CallQueueEntityMapper {

    public CallQueue toCallQueue(CallQueueEntity entity, Map<Long, Set<String>> userSkills) {
        if (entity == null) {
            return null;
        }
        List<CallQueueMember> members = entity.getMembers().stream()
                .map(m -> new CallQueueMember(m.getUserId(), m.getPriority(),
                        userSkills != null ? userSkills.getOrDefault(m.getUserId(), Set.of()) : Set.of()))
                .toList();
        return new CallQueue(entity.getId(), entity.getCompanyId(), entity.getName(), entity.getStrategy(), entity.getRingTimeoutSec(),
                entity.getWrapUpSec(), entity.getMaxWaitSec(), entity.getMaxLength(), entity.getAnnouncePositionEverySec(),
                entity.isAnnounceWaitTime(), entity.getPeriodicAnnounceText(), entity.getPeriodicAnnounceEverySec(),
                entity.getHoldMusicFileId(), entity.getCallbackOfferAfterSec(), entity.getCallbackCampaignId(),
                entity.getOverflowAction(), entity.getOverflowTarget(), entity.getServiceLevelSec(), entity.getStickySeconds(),
                entity.isPostCallSurvey(), entity.getWaitingAgentId(), entity.isAutoCallback(), entity.isEnabled(), members,
                entity.getCreatedAt());
    }

    public CallQueue toCallQueue(CallQueueEntity entity) {
        return toCallQueue(entity, Map.of());
    }

    public List<CallQueueMemberEntity> toCallQueueMemberEntities(List<CallQueueMember> members) {
        if (members == null) {
            return List.of();
        }
        return members.stream()
                .map(m -> new CallQueueMemberEntity(m.userId(), m.priority()))
                .toList();
    }
}
