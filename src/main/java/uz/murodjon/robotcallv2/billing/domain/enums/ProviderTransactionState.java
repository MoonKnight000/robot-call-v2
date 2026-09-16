package uz.murodjon.robotcallv2.billing.domain.enums;

/**
 * Where a payment a provider (Payme, Click) started against a top-up stands.
 *
 * <p>CREATED → PERFORMED (the balance was credited) or CREATED → CANCELLED. There is no way
 * back from PERFORMED: a credited balance may already have been spent on calls, so a refund
 * is for a person to decide, not for a callback.
 */
public enum ProviderTransactionState {
    CREATED,
    PERFORMED,
    CANCELLED
}
