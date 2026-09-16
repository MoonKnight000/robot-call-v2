package uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** One row of call_queue_member, owned by its queue. */
@Embeddable
public class CallQueueMemberEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int priority;

    /** Comma-separated tags. */
    @Column
    private String skills;

    protected CallQueueMemberEntity() {
    }

    public CallQueueMemberEntity(Long userId, int priority, String skills) {
        this.userId = userId;
        this.priority = priority;
        this.skills = skills;
    }

    public Long getUserId() {
        return userId;
    }

    public int getPriority() {
        return priority;
    }

    public String getSkills() {
        return skills;
    }
}
