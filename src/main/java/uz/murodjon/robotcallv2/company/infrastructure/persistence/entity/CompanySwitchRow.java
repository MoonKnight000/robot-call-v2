package uz.murodjon.robotcallv2.company.infrastructure.persistence.entity;

import java.time.Instant;

/** A company's on/off row for one switchable thing (a feature, an integration provider). */
public interface CompanySwitchRow {

    boolean isEnabled();

    void setEnabled(boolean enabled);

    Instant getUpdatedAt();

    void setUpdatedAt(Instant updatedAt);
}
