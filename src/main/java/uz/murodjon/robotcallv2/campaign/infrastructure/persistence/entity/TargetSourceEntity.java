package uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import uz.murodjon.robotcallv2.campaign.domain.enums.TargetSourceMethod;

import java.time.Instant;

/** JPA entity for campaign_target_source (V13) — one row per campaign, keyed by it. */
@Entity
@Table(name = "campaign_target_source")
public class TargetSourceEntity {

    @Id
    @Column(name = "campaign_id")
    private Long campaignId;

    @Column(nullable = false)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "http_method", nullable = false)
    private TargetSourceMethod httpMethod = TargetSourceMethod.GET;

    @Column(name = "request_body")
    private String requestBody;

    @Column(name = "auth_header_name")
    private String authHeaderName;

    @Column(name = "auth_header_value")
    private String authHeaderValue;

    @Column(name = "items_path")
    private String itemsPath;

    @Column(name = "phone_field", nullable = false)
    private String phoneField = "phone";

    @Column(name = "client_id_field")
    private String clientIdField;

    @Column(name = "language_field")
    private String languageField;

    @Column(name = "replace_targets", nullable = false)
    private boolean replaceTargets;

    @Column(name = "sync_on_recurrence", nullable = false)
    private boolean syncOnRecurrence = true;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "last_sync_added")
    private Integer lastSyncAdded;

    @Column(name = "last_sync_error")
    private String lastSyncError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(Long campaignId) {
        this.campaignId = campaignId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public TargetSourceMethod getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(TargetSourceMethod httpMethod) {
        this.httpMethod = httpMethod != null ? httpMethod : TargetSourceMethod.GET;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public String getAuthHeaderName() {
        return authHeaderName;
    }

    public void setAuthHeaderName(String authHeaderName) {
        this.authHeaderName = authHeaderName;
    }

    public String getAuthHeaderValue() {
        return authHeaderValue;
    }

    public void setAuthHeaderValue(String authHeaderValue) {
        this.authHeaderValue = authHeaderValue;
    }

    public String getItemsPath() {
        return itemsPath;
    }

    public void setItemsPath(String itemsPath) {
        this.itemsPath = itemsPath;
    }

    public String getPhoneField() {
        return phoneField;
    }

    public void setPhoneField(String phoneField) {
        this.phoneField = phoneField != null && !phoneField.isBlank() ? phoneField : "phone";
    }

    public String getClientIdField() {
        return clientIdField;
    }

    public void setClientIdField(String clientIdField) {
        this.clientIdField = clientIdField;
    }

    public String getLanguageField() {
        return languageField;
    }

    public void setLanguageField(String languageField) {
        this.languageField = languageField;
    }

    public boolean isReplaceTargets() {
        return replaceTargets;
    }

    public void setReplaceTargets(boolean replaceTargets) {
        this.replaceTargets = replaceTargets;
    }

    public boolean isSyncOnRecurrence() {
        return syncOnRecurrence;
    }

    public void setSyncOnRecurrence(boolean syncOnRecurrence) {
        this.syncOnRecurrence = syncOnRecurrence;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(Instant lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }

    public Integer getLastSyncAdded() {
        return lastSyncAdded;
    }

    public void setLastSyncAdded(Integer lastSyncAdded) {
        this.lastSyncAdded = lastSyncAdded;
    }

    public String getLastSyncError() {
        return lastSyncError;
    }

    public void setLastSyncError(String lastSyncError) {
        this.lastSyncError = lastSyncError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
