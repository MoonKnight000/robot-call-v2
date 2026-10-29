package uz.murodjon.robotcallv2.phonenumber.domain.enums;

/**
 * Lifecycle status of a phone number: in the pool for sale, or assigned to a company. Releasing a number puts it back to AVAILABLE.
 */
public enum PhoneNumberStatus {
    AVAILABLE,
    ASSIGNED
}
