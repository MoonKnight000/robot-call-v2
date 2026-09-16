package uz.murodjon.robotcallv2.schedule.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** JPA entity for business_schedule and its slots. */
@Entity
@Table(name = "business_schedule")
public class BusinessScheduleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "company_id", insertable = false, updatable = false)
    private Long companyId;

    @Column(nullable = false)
    private String name;

    @Column
    private String timezone;

    // Eager (the mapper reads it outside any transaction); SUBSELECT: one query for a
    // whole list of schedules' slots instead of one per schedule.
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "business_schedule_slot", joinColumns = @JoinColumn(name = "schedule_id"))
    @OrderColumn(name = "ord")
    private List<BusinessScheduleSlotEntity> slots = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public List<BusinessScheduleSlotEntity> getSlots() {
        return slots;
    }

    /** Replaced in place: Hibernate tracks the collection instance it loaded. */
    public void setSlots(List<BusinessScheduleSlotEntity> replacement) {
        slots.clear();
        slots.addAll(replacement);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
