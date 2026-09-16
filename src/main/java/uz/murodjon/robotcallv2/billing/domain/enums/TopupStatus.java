package uz.murodjon.robotcallv2.billing.domain.enums;

import java.util.Set;

public enum TopupStatus {
    PENDING,
    COMPLETED,
    FAILED,
    CANCELLED,

    /**
     * Left unpaid past {@code voice-agent.billing.payment.topup-ttl}. No new payment may
     * start on it, but one the provider had already started is still settled — money that
     * actually landed is never refused.
     */
    EXPIRED;

    /** The statuses a payment already started on a top-up may still be settled from — see {@link #EXPIRED}. */
    public static final Set<TopupStatus> SETTLEABLE = Set.of(PENDING, EXPIRED);
}
