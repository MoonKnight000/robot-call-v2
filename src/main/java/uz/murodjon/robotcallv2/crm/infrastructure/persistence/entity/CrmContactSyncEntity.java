package uz.murodjon.robotcallv2.crm.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactFieldMapping;
import uz.murodjon.robotcallv2.crm.domain.enums.CrmContactSyncStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** JPA entity for crm_contact_sync: one company's sync settings and run state. */
@Entity
@Table(name = "crm_contact_sync")
public class CrmContactSyncEntity {

    @Id
    @Column(name = "company_id")
    private Long companyId;

    @Column(nullable = false)
    private boolean enabled;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_mappings", columnDefinition = "jsonb", nullable = false)
    private List<CrmContactFieldMapping> fieldMappings = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "push_mappings", columnDefinition = "jsonb", nullable = false)
    private List<CrmContactFieldMapping> pushMappings = new ArrayList<>();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public List<CrmContactFieldMapping> getPushMappings() {
        return pushMappings;
    }

    public void setPushMappings(List<CrmContactFieldMapping> pushMappings) {
        this.pushMappings = pushMappings != null ? new ArrayList<>(pushMappings) : new ArrayList<>();
    }

    @Column(length = 16)
    private String provider;

    @Column(name = "sync_cursor")
    private Instant cursor;

    @Column(name = "resume_since")
    private Instant resumeSince;

    @Column(name = "resume_page_token", length = 64)
    private String resumePageToken;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_status", length = 16)
    private CrmContactSyncStatus lastStatus;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "last_created", nullable = false)
    private int lastCreated;

    @Column(name = "last_updated", nullable = false)
    private int lastUpdated;

    @Column(name = "last_skipped", nullable = false)
    private int lastSkipped;

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<CrmContactFieldMapping> getFieldMappings() {
        return fieldMappings;
    }

    public void setFieldMappings(List<CrmContactFieldMapping> fieldMappings) {
        this.fieldMappings = fieldMappings != null ? new ArrayList<>(fieldMappings) : new ArrayList<>();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Instant getCursor() {
        return cursor;
    }

    public void setCursor(Instant cursor) {
        this.cursor = cursor;
    }

    public Instant getResumeSince() {
        return resumeSince;
    }

    public void setResumeSince(Instant resumeSince) {
        this.resumeSince = resumeSince;
    }

    public String getResumePageToken() {
        return resumePageToken;
    }

    public void setResumePageToken(String resumePageToken) {
        this.resumePageToken = resumePageToken;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(Instant nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(Instant lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public CrmContactSyncStatus getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(CrmContactSyncStatus lastStatus) {
        this.lastStatus = lastStatus;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public int getLastCreated() {
        return lastCreated;
    }

    public void setLastCreated(int lastCreated) {
        this.lastCreated = lastCreated;
    }

    public int getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(int lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public int getLastSkipped() {
        return lastSkipped;
    }

    public void setLastSkipped(int lastSkipped) {
        this.lastSkipped = lastSkipped;
    }
}
