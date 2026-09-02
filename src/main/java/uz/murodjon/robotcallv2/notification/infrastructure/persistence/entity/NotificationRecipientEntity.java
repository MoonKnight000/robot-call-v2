package uz.murodjon.robotcallv2.notification.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

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

    @Column(name = "user_id", nullable = false)
    private long userId;

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

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }
}
