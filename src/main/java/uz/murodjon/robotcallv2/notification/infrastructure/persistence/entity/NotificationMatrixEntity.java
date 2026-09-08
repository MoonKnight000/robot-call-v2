package uz.murodjon.robotcallv2.notification.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationChannelType;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;

@Entity
@Table(name = "notification_matrix")
public class NotificationMatrixEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "company_id", insertable = false, updatable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannelType channel;

    @Column(nullable = false)
    private boolean enabled;

    public Long getId() {
        return id;
    }

    public CompanyEntity getCompany() {
        return company;
    }

    public void setCompany(CompanyEntity company) {
        this.company = company;
    }

    public long getCompanyId() {
        return company != null ? company.getId() : 0L;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public NotificationChannelType getChannel() {
        return channel;
    }

    public void setChannel(NotificationChannelType channel) {
        this.channel = channel;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
