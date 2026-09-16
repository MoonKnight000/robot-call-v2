package uz.murodjon.robotcallv2.pack.domain.entity;

/**
 * A secret a pack's tools read through {@code {{secrets.KEY}}}. Installing never fills it —
 * the value is the company's own shop token or address, set afterwards in the secrets store.
 *
 * @param description what the value is, for the person filling it in
 */
public record PackSecret(String key, String description) {
}
