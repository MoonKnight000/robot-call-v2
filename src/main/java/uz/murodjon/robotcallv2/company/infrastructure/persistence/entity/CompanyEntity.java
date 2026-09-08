package uz.murodjon.robotcallv2.company.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;
import uz.murodjon.robotcallv2.storage.infrastructure.persistence.entity.StoredFileEntity;

import java.time.Instant;

/**
 * JPA entity for company (ROADMAP B.1).
 */
@Entity
@Table(name = "company")
public class CompanyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompanyStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "logo_file_id")
    private StoredFileEntity logoFile;

    @Column(name = "address")
    private String address;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CompanyStatus getStatus() {
        return status;
    }

    public void setStatus(CompanyStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public StoredFileEntity getLogoFile() {
        return logoFile;
    }

    public void setLogoFile(StoredFileEntity logoFile) {
        this.logoFile = logoFile;
    }

    public Long getLogoFileId() {
        return logoFile != null ? logoFile.getId() : null;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
