package uz.murodjon.robotcallv2.user.domain.enums;

/**
 * What an admin tried to do to an account, named in {@code SELF_ACTION_FORBIDDEN} and
 * {@code LAST_ADMIN_ACTION_FORBIDDEN}. {@link #toString()} is the phrase those messages
 * interpolate, so the text the API returns stays exactly what it was.
 */
public enum UserAction {

    CHANGE_ROLE("change the role of"),
    BLOCK("block");

    private final String phrase;

    UserAction(String phrase) {
        this.phrase = phrase;
    }

    @Override
    public String toString() {
        return phrase;
    }
}
