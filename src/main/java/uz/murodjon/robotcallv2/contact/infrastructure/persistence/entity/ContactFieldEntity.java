package uz.murodjon.robotcallv2.contact.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.contact.domain.enums.ContactFieldType;
import uz.murodjon.robotcallv2.contact.domain.enums.ContactFieldUpdateMode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** JPA entity for contact_field. */
@Entity
@Table(name = "contact_field")
public class ContactFieldEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    @Column(name = "field_key", nullable = false, length = 64)
    private String key;

    @Column(nullable = false, length = 128)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 16)
    private ContactFieldType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<String> options = new ArrayList<>();

    @Column
    private String description;

    @Column(name = "extraction_key", length = 64)
    private String extractionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "update_mode", nullable = false, length = 16)
    private ContactFieldUpdateMode updateMode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public CompanyEntity getCompany() {
        return company;
    }

    public void setCompany(CompanyEntity company) {
        this.company = company;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public ContactFieldType getType() {
        return type;
    }

    public void setType(ContactFieldType type) {
        this.type = type;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options != null ? new ArrayList<>(options) : new ArrayList<>();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExtractionKey() {
        return extractionKey;
    }

    public void setExtractionKey(String extractionKey) {
        this.extractionKey = extractionKey;
    }

    public ContactFieldUpdateMode getUpdateMode() {
        return updateMode;
    }

    public void setUpdateMode(ContactFieldUpdateMode updateMode) {
        this.updateMode = updateMode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
