package uz.murodjon.robotcallv2.chat.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.dialog.Sentiment;

import java.time.Instant;

/** Plain id columns, like {@code call_quality}: written by one request and read back whole. */
@Entity
@Table(name = "chat_session")
public class ChatSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_key", nullable = false, length = 64)
    private String sessionKey;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "ai_agent_id", nullable = false)
    private Long aiAgentId;

    @Column(name = "widget_id")
    private Long widgetId;

    @Column(name = "telegram_bot_id")
    private Long telegramBotId;

    @Column(name = "telegram_chat_id", length = 64)
    private String telegramChatId;

    @Column(name = "language", nullable = false, length = 10)
    private String language;

    @Column(name = "stage", nullable = false, length = 100)
    private String stage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "outcome", nullable = false, columnDefinition = "jsonb")
    private String outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "disposition", length = 50)
    private Disposition disposition;

    @Column(name = "ended", nullable = false)
    private boolean ended;

    @Column(name = "message_count", nullable = false)
    private int messageCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;

    @Column(name = "operator_user_id")
    private Long operatorUserId;

    @Column(name = "analysis_summary", columnDefinition = "text")
    private String analysisSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_sentiment", length = 20)
    private Sentiment analysisSentiment;

    @Column(name = "analysis_needs_follow_up")
    private Boolean analysisNeedsFollowUp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "analysis_outcome", columnDefinition = "jsonb")
    private String analysisOutcome;

    @Column(name = "analyzed_at")
    private Instant analyzedAt;

    public Long getOperatorUserId() { return operatorUserId; }

    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }

    public String getAnalysisSummary() { return analysisSummary; }

    public void setAnalysisSummary(String analysisSummary) { this.analysisSummary = analysisSummary; }

    public Sentiment getAnalysisSentiment() { return analysisSentiment; }

    public void setAnalysisSentiment(Sentiment analysisSentiment) { this.analysisSentiment = analysisSentiment; }

    public Boolean getAnalysisNeedsFollowUp() { return analysisNeedsFollowUp; }

    public void setAnalysisNeedsFollowUp(Boolean analysisNeedsFollowUp) { this.analysisNeedsFollowUp = analysisNeedsFollowUp; }

    public String getAnalysisOutcome() { return analysisOutcome; }

    public void setAnalysisOutcome(String analysisOutcome) { this.analysisOutcome = analysisOutcome; }

    public Instant getAnalyzedAt() { return analyzedAt; }

    public void setAnalyzedAt(Instant analyzedAt) { this.analyzedAt = analyzedAt; }

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public String getSessionKey() { return sessionKey; }

    public void setSessionKey(String sessionKey) { this.sessionKey = sessionKey; }

    public Long getCompanyId() { return companyId; }

    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public Long getAiAgentId() { return aiAgentId; }

    public void setAiAgentId(Long aiAgentId) { this.aiAgentId = aiAgentId; }

    public Long getWidgetId() { return widgetId; }

    public void setWidgetId(Long widgetId) { this.widgetId = widgetId; }

    public Long getTelegramBotId() { return telegramBotId; }

    public void setTelegramBotId(Long telegramBotId) { this.telegramBotId = telegramBotId; }

    public String getTelegramChatId() { return telegramChatId; }

    public void setTelegramChatId(String telegramChatId) { this.telegramChatId = telegramChatId; }

    public String getLanguage() { return language; }

    public void setLanguage(String language) { this.language = language; }

    public String getStage() { return stage; }

    public void setStage(String stage) { this.stage = stage; }

    public String getOutcome() { return outcome; }

    public void setOutcome(String outcome) { this.outcome = outcome; }

    public Disposition getDisposition() { return disposition; }

    public void setDisposition(Disposition disposition) { this.disposition = disposition; }

    public boolean isEnded() { return ended; }

    public void setEnded(boolean ended) { this.ended = ended; }

    public int getMessageCount() { return messageCount; }

    public void setMessageCount(int messageCount) { this.messageCount = messageCount; }

    public Instant getCreatedAt() { return createdAt; }

    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastMessageAt() { return lastMessageAt; }

    public void setLastMessageAt(Instant lastMessageAt) { this.lastMessageAt = lastMessageAt; }
}
