package uz.murodjon.robotcallv2.agent.dialog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Persistent store for colloquial barge-in words and dialect phrases learned by the model.
 *
 * <p>Serves as the durable source of truth. At application startup, approved and active rules
 * are loaded into {@link FloorLearning}'s in-memory cache for zero-latency lookups during calls.
 */
@Entity
@Table(name = "learned_floor_rule")
public class LearnedFloorRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String phrase;

    @Column(nullable = false, length = 20)
    private String decision; // "STOP" or "CONTINUE"

    @Column(nullable = false)
    private int occurrences;

    @Column(nullable = false, length = 20)
    private String status; // "LEARNING", "APPROVED", "BLOCKED"

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public LearnedFloorRuleEntity() {
    }

    public LearnedFloorRuleEntity(String phrase, String decision, int occurrences, String status,
                                  Instant createdAt, Instant updatedAt) {
        this.phrase = phrase;
        this.decision = decision;
        this.occurrences = occurrences;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPhrase() {
        return phrase;
    }

    public void setPhrase(String phrase) {
        this.phrase = phrase;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public int getOccurrences() {
        return occurrences;
    }

    public void setOccurrences(int occurrences) {
        this.occurrences = occurrences;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
