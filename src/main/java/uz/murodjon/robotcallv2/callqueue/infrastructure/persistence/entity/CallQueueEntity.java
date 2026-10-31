package uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import uz.murodjon.robotcallv2.callqueue.domain.enums.CallQueueStrategy;
import uz.murodjon.robotcallv2.callqueue.domain.enums.QueueOverflowAction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** JPA entity for call_queue and its members. */
@Entity
@Table(name = "call_queue")
public class CallQueueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallQueueStrategy strategy = CallQueueStrategy.LEAST_RECENT;

    @Column(name = "ring_timeout_sec", nullable = false)
    private int ringTimeoutSec;

    @Column(name = "wrap_up_sec", nullable = false)
    private int wrapUpSec;

    @Column(name = "max_wait_sec", nullable = false)
    private int maxWaitSec;

    @Column(name = "max_length", nullable = false)
    private int maxLength;

    @Column(name = "announce_position_every_sec", nullable = false)
    private int announcePositionEverySec;

    @Column(name = "announce_wait_time", nullable = false)
    private boolean announceWaitTime;

    @Column(name = "periodic_announce_text", columnDefinition = "TEXT")
    private String periodicAnnounceText;

    @Column(name = "periodic_announce_every_sec", nullable = false)
    private int periodicAnnounceEverySec;

    @Column(name = "hold_music_file_id")
    private Long holdMusicFileId;

    @Column(name = "callback_offer_after_sec", nullable = false)
    private int callbackOfferAfterSec;

    @Column(name = "callback_campaign_id")
    private Long callbackCampaignId;

    @Enumerated(EnumType.STRING)
    @Column(name = "overflow_action", nullable = false)
    private QueueOverflowAction overflowAction = QueueOverflowAction.CALLBACK;

    @Column(name = "overflow_queue_id")
    private Long overflowQueueId;

    @Column(name = "overflow_agent_id")
    private Long overflowAgentId;

    @Column(name = "overflow_voicemail_id")
    private Long overflowVoicemailId;

    @Column(name = "overflow_external_phone", length = 32)
    private String overflowExternalPhone;

    @Column(name = "service_level_sec", nullable = false)
    private int serviceLevelSec;

    @Column(name = "sticky_seconds", nullable = false)
    private int stickySeconds;

    @Column(name = "post_call_survey", nullable = false)
    private boolean postCallSurvey;

    @Column(name = "waiting_agent_id")
    private Long waitingAgentId;

    @Column(name = "auto_callback", nullable = false)
    private boolean autoCallback;

    @Column(nullable = false)
    private boolean enabled = true;

    // Eager (the mapper reads it outside any transaction); SUBSELECT: one query for a
    // whole list of queues' members instead of one per queue.
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "call_queue_member", joinColumns = @JoinColumn(name = "queue_id"))
    private List<CallQueueMemberEntity> members = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }

    public Long getCompanyId() { return companyId; }

    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public CallQueueStrategy getStrategy() { return strategy; }

    public void setStrategy(CallQueueStrategy strategy) { this.strategy = strategy; }

    public int getRingTimeoutSec() { return ringTimeoutSec; }

    public void setRingTimeoutSec(int ringTimeoutSec) { this.ringTimeoutSec = ringTimeoutSec; }

    public int getWrapUpSec() { return wrapUpSec; }

    public void setWrapUpSec(int wrapUpSec) { this.wrapUpSec = wrapUpSec; }

    public int getMaxWaitSec() { return maxWaitSec; }

    public void setMaxWaitSec(int maxWaitSec) { this.maxWaitSec = maxWaitSec; }

    public int getMaxLength() { return maxLength; }

    public void setMaxLength(int maxLength) { this.maxLength = maxLength; }

    public int getAnnouncePositionEverySec() { return announcePositionEverySec; }

    public void setAnnouncePositionEverySec(int value) { this.announcePositionEverySec = value; }

    public boolean isAnnounceWaitTime() { return announceWaitTime; }

    public void setAnnounceWaitTime(boolean announceWaitTime) { this.announceWaitTime = announceWaitTime; }

    public String getPeriodicAnnounceText() { return periodicAnnounceText; }

    public void setPeriodicAnnounceText(String periodicAnnounceText) { this.periodicAnnounceText = periodicAnnounceText; }

    public int getPeriodicAnnounceEverySec() { return periodicAnnounceEverySec; }

    public void setPeriodicAnnounceEverySec(int value) { this.periodicAnnounceEverySec = value; }

    public Long getHoldMusicFileId() { return holdMusicFileId; }

    public void setHoldMusicFileId(Long holdMusicFileId) { this.holdMusicFileId = holdMusicFileId; }

    public int getCallbackOfferAfterSec() { return callbackOfferAfterSec; }

    public void setCallbackOfferAfterSec(int value) { this.callbackOfferAfterSec = value; }

    public Long getCallbackCampaignId() { return callbackCampaignId; }

    public void setCallbackCampaignId(Long callbackCampaignId) { this.callbackCampaignId = callbackCampaignId; }

    public QueueOverflowAction getOverflowAction() { return overflowAction; }

    public void setOverflowAction(QueueOverflowAction overflowAction) { this.overflowAction = overflowAction; }

    public Long getOverflowQueueId() { return overflowQueueId; }
    public void setOverflowQueueId(Long overflowQueueId) { this.overflowQueueId = overflowQueueId; }

    public Long getOverflowAgentId() { return overflowAgentId; }
    public void setOverflowAgentId(Long overflowAgentId) { this.overflowAgentId = overflowAgentId; }

    public Long getOverflowVoicemailId() { return overflowVoicemailId; }
    public void setOverflowVoicemailId(Long overflowVoicemailId) { this.overflowVoicemailId = overflowVoicemailId; }

    public String getOverflowExternalPhone() { return overflowExternalPhone; }
    public void setOverflowExternalPhone(String overflowExternalPhone) { this.overflowExternalPhone = overflowExternalPhone; }

    public String getOverflowTarget() {
        if (overflowAction == null) {
            return null;
        }
        return switch (overflowAction) {
            case QUEUE -> overflowQueueId != null ? String.valueOf(overflowQueueId) : null;
            case AI_AGENT -> overflowAgentId != null ? String.valueOf(overflowAgentId) : null;
            case VOICEMAIL -> overflowVoicemailId != null ? String.valueOf(overflowVoicemailId) : null;
            case EXTERNAL_NUMBER -> overflowExternalPhone;
            default -> null;
        };
    }

    public void setOverflowTarget(String overflowTarget) {
        setOverflowTargetForAction(this.overflowAction, overflowTarget);
    }

    public void setOverflowTargetForAction(QueueOverflowAction action, String target) {
        this.overflowQueueId = null;
        this.overflowAgentId = null;
        this.overflowVoicemailId = null;
        this.overflowExternalPhone = null;
        if (target == null || target.isBlank() || action == null) {
            return;
        }
        String trimmed = target.trim();
        switch (action) {
            case QUEUE -> this.overflowQueueId = parseLongOrNull(trimmed);
            case AI_AGENT -> this.overflowAgentId = parseLongOrNull(trimmed);
            case VOICEMAIL -> this.overflowVoicemailId = parseLongOrNull(trimmed);
            case EXTERNAL_NUMBER -> this.overflowExternalPhone = trimmed;
            default -> { /* no-op */ }
        }
    }

    private static Long parseLongOrNull(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public int getServiceLevelSec() { return serviceLevelSec; }

    public void setServiceLevelSec(int serviceLevelSec) { this.serviceLevelSec = serviceLevelSec; }

    public int getStickySeconds() { return stickySeconds; }

    public void setStickySeconds(int stickySeconds) { this.stickySeconds = stickySeconds; }

    public boolean isPostCallSurvey() { return postCallSurvey; }

    public void setPostCallSurvey(boolean postCallSurvey) { this.postCallSurvey = postCallSurvey; }

    public Long getWaitingAgentId() { return waitingAgentId; }

    public void setWaitingAgentId(Long waitingAgentId) { this.waitingAgentId = waitingAgentId; }

    public boolean isAutoCallback() { return autoCallback; }

    public void setAutoCallback(boolean autoCallback) { this.autoCallback = autoCallback; }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<CallQueueMemberEntity> getMembers() { return members; }

    /** Replaced in place: Hibernate tracks the collection instance it loaded. */
    public void setMembers(List<CallQueueMemberEntity> replacement) {
        members.clear();
        members.addAll(replacement);
    }

    public Instant getCreatedAt() { return createdAt; }

    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
