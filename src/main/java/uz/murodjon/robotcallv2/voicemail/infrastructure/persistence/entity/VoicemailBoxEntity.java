package uz.murodjon.robotcallv2.voicemail.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

/** JPA entity for voicemail_box. */
@Entity
@Table(name = "voicemail_box")
public class VoicemailBoxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private String name;

    @Column(name = "greeting_text", columnDefinition = "TEXT")
    private String greetingText;

    @Column(name = "greeting_file_id")
    private Long greetingFileId;

    @Column(name = "max_seconds", nullable = false)
    private int maxSeconds;

    @Column(nullable = false)
    private boolean transcribe = true;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGreetingText() {
        return greetingText;
    }

    public void setGreetingText(String greetingText) {
        this.greetingText = greetingText;
    }

    public Long getGreetingFileId() {
        return greetingFileId;
    }

    public void setGreetingFileId(Long greetingFileId) {
        this.greetingFileId = greetingFileId;
    }

    public int getMaxSeconds() {
        return maxSeconds;
    }

    public void setMaxSeconds(int maxSeconds) {
        this.maxSeconds = maxSeconds;
    }

    public boolean isTranscribe() {
        return transcribe;
    }

    public void setTranscribe(boolean transcribe) {
        this.transcribe = transcribe;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
