package uz.murodjon.uysotvoice.voice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One selectable voice (PROJECT.md §2.5). A campaign stores {@link #getId()}; this row
 * is what turns it back into a provider plus a provider-side voice name, so the choice
 * pins both — a voice is only ever spoken by the engine that owns it.
 */
@Entity
@Table(name = "tts_voice")
public class TtsVoiceEntity {

    /** Stable id stored on {@code campaign.tts_voice} (e.g. {@code nigora}), not a generated PK. */
    @Id
    private String id;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String language;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String label;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
