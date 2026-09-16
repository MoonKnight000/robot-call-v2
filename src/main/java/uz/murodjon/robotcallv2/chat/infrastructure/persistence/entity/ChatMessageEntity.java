package uz.murodjon.robotcallv2.chat.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.chat.domain.enums.ChatRole;

import java.time.Instant;

@Entity
@Table(name = "chat_message")
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_session_id", nullable = false)
    private Long chatSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private ChatRole role;

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public Long getChatSessionId() { return chatSessionId; }

    public void setChatSessionId(Long chatSessionId) { this.chatSessionId = chatSessionId; }

    public ChatRole getRole() { return role; }

    public void setRole(ChatRole role) { this.role = role; }

    public String getText() { return text; }

    public void setText(String text) { this.text = text; }

    public Instant getCreatedAt() { return createdAt; }

    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
