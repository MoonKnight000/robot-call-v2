package uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** Composite primary key for call_queue_member_skill: (queue_id, user_id, skill). */
@Embeddable
public class CallQueueMemberSkillId implements Serializable {

    @Column(name = "queue_id", nullable = false)
    private Long queueId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "skill", nullable = false, length = 64)
    private String skill;

    protected CallQueueMemberSkillId() {
    }

    public CallQueueMemberSkillId(Long queueId, Long userId, String skill) {
        this.queueId = queueId;
        this.userId = userId;
        this.skill = skill;
    }

    public Long getQueueId() {
        return queueId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getSkill() {
        return skill;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CallQueueMemberSkillId that
                && Objects.equals(queueId, that.queueId)
                && Objects.equals(userId, that.userId)
                && Objects.equals(skill, that.skill);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queueId, userId, skill);
    }
}
