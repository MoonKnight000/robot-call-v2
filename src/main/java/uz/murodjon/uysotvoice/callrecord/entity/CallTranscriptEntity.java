package uz.murodjon.uysotvoice.callrecord.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** JPA entity for {@code call_transcript} (PROJECT.md §6, Stage 9), one row per utterance. */
@Entity
@Table(name = "call_transcript")
public class CallTranscriptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "call_id", nullable = false)
    private CallAttemptEntity call;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private String text;

    @Column(name = "dialog_state")
    private String dialogState;

    @Column(name = "ts_offset_ms", nullable = false)
    private int tsOffsetMs;

    @Column(name = "stt_confidence")
    private Float sttConfidence;

    public Long getId() {
        return id;
    }

    public CallAttemptEntity getCall() {
        return call;
    }

    public void setCall(CallAttemptEntity call) {
        this.call = call;
    }

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getDialogState() {
        return dialogState;
    }

    public void setDialogState(String dialogState) {
        this.dialogState = dialogState;
    }

    public int getTsOffsetMs() {
        return tsOffsetMs;
    }

    public void setTsOffsetMs(int tsOffsetMs) {
        this.tsOffsetMs = tsOffsetMs;
    }

    public Float getSttConfidence() {
        return sttConfidence;
    }

    public void setSttConfidence(Float sttConfidence) {
        this.sttConfidence = sttConfidence;
    }
}
