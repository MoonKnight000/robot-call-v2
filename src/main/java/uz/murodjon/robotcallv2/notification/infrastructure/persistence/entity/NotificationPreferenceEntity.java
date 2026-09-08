package uz.murodjon.robotcallv2.notification.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.entity.UserEntity;

@Entity
@Table(name = "notification_preference")
public class NotificationPreferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private boolean enabled;

    public Long getId() {
        return id;
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

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
