package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository;

import java.time.Instant;

public interface CallReanalysisTargetProjection {
    Long getId();
    Instant getStartedAt();
}
