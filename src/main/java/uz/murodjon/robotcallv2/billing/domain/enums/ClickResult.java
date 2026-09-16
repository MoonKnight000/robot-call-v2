package uz.murodjon.robotcallv2.billing.domain.enums;

/**
 * The {@code error} codes a merchant answers the Click SHOP API with, and the
 * {@code error_note} that goes with each.
 *
 * <p>The codes are Click's, not ours: changing one breaks the protocol.
 */
public enum ClickResult {

    SUCCESS(0, "Success"),
    SIGN_CHECK_FAILED(-1, "SIGN CHECK FAILED!"),
    INCORRECT_AMOUNT(-2, "Incorrect parameter amount"),
    ACTION_NOT_FOUND(-3, "Action not found"),
    ALREADY_PAID(-4, "Already paid"),
    TOPUP_NOT_FOUND(-5, "Top-up does not exist"),
    TRANSACTION_NOT_FOUND(-6, "Transaction does not exist"),
    BAD_REQUEST(-8, "Error in request from click"),
    TRANSACTION_CANCELLED(-9, "Transaction cancelled");

    private final int code;
    private final String note;

    ClickResult(int code, String note) {
        this.code = code;
        this.note = note;
    }

    public int code() {
        return code;
    }

    public String note() {
        return note;
    }
}
