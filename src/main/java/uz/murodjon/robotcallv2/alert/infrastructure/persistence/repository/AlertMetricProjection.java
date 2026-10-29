package uz.murodjon.robotcallv2.alert.infrastructure.persistence.repository;

/** One window's figures; each query fills {@code total} and either {@code hits} or {@code average}. */
public interface AlertMetricProjection {
    long getTotal();
    Long getHits();
    Double getAverage();
}
