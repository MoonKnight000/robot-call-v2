package uz.murodjon.robotcallv2.notification.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.entity.UserEntity;

/** JPA entity for notification_recipient — links to NotificationEntity. */
@Entity
@Table(name = "notification_recipient")
public class NotificationRecipientEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id", nullable = false)
    private NotificationEntity notification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @Column(name = "read_at")
    private Instant readAt;

    public Long getId() {
        return id;
    }

    public NotificationEntity getNotification() {
        return notification;
    }

    public void setNotification(NotificationEntity notification) {
        this.notification = notification;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public long getUserId() {
        return user != null ? user.getId() : 0L;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }
}
