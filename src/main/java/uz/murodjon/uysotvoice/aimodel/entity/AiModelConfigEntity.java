package uz.murodjon.uysotvoice.aimodel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * JPA entity for {@code ai_model_config} (§11 settings) — a company's overrides on top
 * of the process-wide Gemini defaults ({@code spring.ai.google.genai.chat.options.*},
 * {@code DialogProperties}). Every override column is nullable: null means "use the
 * process default", not zero.
 */

@Getter
@Setter
@Entity
@Table(name = "ai_model_config")
public class AiModelConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private long companyId;

    @Column
    private String model;

    @Column
    private Double temperature;

    @Column(name = "max_output_tokens")
    private Integer maxOutputTokens;

    @Column(name = "max_call_seconds")
    private Integer maxCallSeconds;

    @Column(name = "max_tokens_per_call")
    private Long maxTokensPerCall;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;


}
