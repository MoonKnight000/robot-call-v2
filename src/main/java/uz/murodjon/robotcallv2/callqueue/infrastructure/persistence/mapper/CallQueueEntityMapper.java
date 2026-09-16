package uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueueMember;
import uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.entity.CallQueueEntity;
import uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.entity.CallQueueMemberEntity;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CallQueueEntityMapper {

    public CallQueue toCallQueue(CallQueueEntity entity) {
        List<CallQueueMember> members = entity.getMembers().stream()
                .map(m -> new CallQueueMember(m.getUserId(), m.getPriority(), splitSkills(m.getSkills())))
                .toList();
        return new CallQueue(entity.getId(), entity.getCompanyId(), entity.getName(), entity.getStrategy(), entity.getRingTimeoutSec(),
                entity.getWrapUpSec(), entity.getMaxWaitSec(), entity.getMaxLength(), entity.getAnnouncePositionEverySec(),
                entity.isAnnounceWaitTime(), entity.getPeriodicAnnounceText(), entity.getPeriodicAnnounceEverySec(),
                entity.getHoldMusicFileId(), entity.getCallbackOfferAfterSec(), entity.getCallbackCampaignId(),
                entity.getOverflowAction(), entity.getOverflowTarget(), entity.getServiceLevelSec(), entity.getStickySeconds(),
                entity.isPostCallSurvey(), entity.getWaitingAgentId(), entity.isAutoCallback(), entity.isEnabled(), members,
                entity.getCreatedAt());
    }

    public List<CallQueueMemberEntity> toCallQueueMemberEntities(List<CallQueueMember> members) {
        return members.stream()
                .map(m -> new CallQueueMemberEntity(m.userId(), m.priority(),
                        m.skills().isEmpty() ? null : String.join(",", m.skills())))
                .toList();
    }

    private static Set<String> splitSkills(String skills) {
        if (skills == null || skills.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(skills.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
