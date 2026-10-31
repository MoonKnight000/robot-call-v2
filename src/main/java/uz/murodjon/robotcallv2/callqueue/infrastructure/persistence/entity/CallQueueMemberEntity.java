package uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/** One row of call_queue_member, owned by its queue. */
@Embeddable
public class CallQueueMemberEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int priority;

    protected CallQueueMemberEntity() {
    }

    public CallQueueMemberEntity(Long userId, int priority) {
        this.userId = userId;
        this.priority = priority;
    }

    public Long getUserId() {
        return userId;
    }

    public int getPriority() {
        return priority;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CallQueueMemberEntity that
                && Objects.equals(userId, that.userId)
                && priority == that.priority;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, priority);
    }
}
