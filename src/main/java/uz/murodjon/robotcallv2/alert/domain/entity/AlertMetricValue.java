package uz.murodjon.robotcallv2.alert.domain.entity;

/**
 * A metric over one window.
 *
 * @param value      null when the window holds nothing to compute it from
 * @param sampleSize the calls (or tool requests) it was computed over
 */
public record AlertMetricValue(Double value, long sampleSize) {
}
