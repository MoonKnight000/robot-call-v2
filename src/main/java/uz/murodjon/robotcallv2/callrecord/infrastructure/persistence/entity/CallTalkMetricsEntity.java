package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA entity for {@code call_talk_metrics}.
 */
@Entity
@Table(name = "call_talk_metrics")
public class CallTalkMetricsEntity {

    @Id
    @Column(name = "call_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "call_id")
    private CallAttemptEntity call;

    @Column(name = "analysed_ms", nullable = false)
    private int analysedMs;

    @Column(name = "client_talk_ms", nullable = false)
    private int clientTalkMs;

    @Column(name = "operator_talk_ms", nullable = false)
    private int operatorTalkMs;

    @Column(name = "overtalk_ms", nullable = false)
    private int overtalkMs;

    @Column(name = "silence_ms", nullable = false)
    private int silenceMs;

    @Column(name = "longest_silence_ms", nullable = false)
    private int longestSilenceMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public CallAttemptEntity getCall() {
        return call;
    }

    public void setCall(CallAttemptEntity call) {
        this.call = call;
    }

    public int getAnalysedMs() {
        return analysedMs;
    }

    public void setAnalysedMs(int analysedMs) {
        this.analysedMs = analysedMs;
    }

    public int getClientTalkMs() {
        return clientTalkMs;
    }

    public void setClientTalkMs(int clientTalkMs) {
        this.clientTalkMs = clientTalkMs;
    }

    public int getOperatorTalkMs() {
        return operatorTalkMs;
    }

    public void setOperatorTalkMs(int operatorTalkMs) {
        this.operatorTalkMs = operatorTalkMs;
    }

    public int getOvertalkMs() {
        return overtalkMs;
    }

    public void setOvertalkMs(int overtalkMs) {
        this.overtalkMs = overtalkMs;
    }

    public int getSilenceMs() {
        return silenceMs;
    }

    public void setSilenceMs(int silenceMs) {
        this.silenceMs = silenceMs;
    }

    public int getLongestSilenceMs() {
        return longestSilenceMs;
    }

    public void setLongestSilenceMs(int longestSilenceMs) {
        this.longestSilenceMs = longestSilenceMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
