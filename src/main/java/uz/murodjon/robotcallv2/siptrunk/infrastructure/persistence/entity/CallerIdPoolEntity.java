package uz.murodjon.robotcallv2.siptrunk.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.CallerIdStrategy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "caller_id_pool")
public class CallerIdPoolEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    @Column(name = "company_id", insertable = false, updatable = false)
    private Long companyId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CallerIdStrategy strategy = CallerIdStrategy.ROUND_ROBIN;

    @Column(length = 255)
    private String description;

    @OneToMany(mappedBy = "pool", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CallerIdPoolNumberEntity> numbers = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CompanyEntity getCompany() {
        return company;
    }

    public void setCompany(CompanyEntity company) {
        this.company = company;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CallerIdStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(CallerIdStrategy strategy) {
        this.strategy = strategy;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<CallerIdPoolNumberEntity> getNumbers() {
        return numbers;
    }

    public void setNumbers(List<CallerIdPoolNumberEntity> numbers) {
        this.numbers = numbers;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
