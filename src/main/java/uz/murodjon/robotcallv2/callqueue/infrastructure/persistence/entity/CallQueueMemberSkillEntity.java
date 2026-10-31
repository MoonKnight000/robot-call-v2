package uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** JPA entity for call_queue_member_skill: normalized skills for an operator in a queue. */
@Entity
@Table(name = "call_queue_member_skill")
public class CallQueueMemberSkillEntity {

    @EmbeddedId
    private CallQueueMemberSkillId id;

    protected CallQueueMemberSkillEntity() {
    }

    public CallQueueMemberSkillEntity(CallQueueMemberSkillId id) {
        this.id = id;
    }

    public CallQueueMemberSkillEntity(Long queueId, Long userId, String skill) {
        this(new CallQueueMemberSkillId(queueId, userId, skill));
    }

    public CallQueueMemberSkillId getId() {
        return id;
    }

    public Long getQueueId() {
        return id != null ? id.getQueueId() : null;
    }

    public Long getUserId() {
        return id != null ? id.getUserId() : null;
    }

    public String getSkill() {
        return id != null ? id.getSkill() : null;
    }
}
