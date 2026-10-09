package uz.murodjon.robotcallv2.promise.infrastructure.persistence.repository;

import java.math.BigDecimal;

public interface PaymentPromiseStatsProjection {

    long getTotal();

    long getOpen();

    long getKept();

    long getBroken();

    long getCancelled();

    BigDecimal getPromisedAmount();

    BigDecimal getKeptAmount();
}
