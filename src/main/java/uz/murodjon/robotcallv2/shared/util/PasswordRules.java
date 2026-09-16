package uz.murodjon.robotcallv2.shared.util;

/**
 * The one password policy, referenced by every request that sets a password (activation,
 * reset, profile change) so the three can never drift apart.
 */
public final class PasswordRules {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 128;
    /** At least one letter (any script) and one digit; length is checked separately. */
    public static final String REGEX = "^(?=.*\\p{L})(?=.*\\d).+$";
    public static final String MESSAGE = "must contain at least one letter and one digit";

    private PasswordRules() {
    }
}
