package uz.murodjon.robotcallv2.report.infrastructure.persistence.repository;

public interface RoiTotalsProjection {
    long getTotalCalls();
    long getAnsweredCalls();
    long getTalkSeconds();
    long getSpentUzs();
    long getPromises();
    long getPromisedAmount();
    long getKeptPromises();
    long getKeptAmount();
    long getConversions();
    long getConversionValue();
}
