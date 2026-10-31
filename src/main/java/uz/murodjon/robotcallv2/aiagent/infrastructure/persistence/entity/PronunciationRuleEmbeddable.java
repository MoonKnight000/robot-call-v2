package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * JPA Embeddable mapping for a single pronunciation rule in {@code ai_agent_pronunciation_rule}.
 */
@Embeddable
public class PronunciationRuleEmbeddable {

    @Column(name = "word", nullable = false, length = 128)
    private String word;

    @Column(name = "replacement", nullable = false, length = 256)
    private String replacement;

    @Column(name = "case_sensitive", nullable = false)
    private boolean caseSensitive;

    @Column(name = "language", length = 10)
    private String language = "*";

    protected PronunciationRuleEmbeddable() {
    }

    public PronunciationRuleEmbeddable(String word, String replacement, boolean caseSensitive, String language) {
        this.word = word;
        this.replacement = replacement;
        this.caseSensitive = caseSensitive;
        this.language = language != null ? language : "*";
    }

    public String getWord() {
        return word;
    }

    public String getReplacement() {
        return replacement;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public String getLanguage() {
        return language;
    }
}
