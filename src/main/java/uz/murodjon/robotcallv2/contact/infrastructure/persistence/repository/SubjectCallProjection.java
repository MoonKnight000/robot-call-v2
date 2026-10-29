package uz.murodjon.robotcallv2.contact.infrastructure.persistence.repository;

import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;

public interface SubjectCallProjection {
    Long getId();
    Instant getStartedAt();
    Integer getDurationSec();
    Disposition getDisposition();
    String getSummary();
}
