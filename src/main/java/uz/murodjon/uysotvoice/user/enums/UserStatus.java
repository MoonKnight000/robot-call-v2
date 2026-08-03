package uz.murodjon.uysotvoice.user.enums;

/** {@code app_user.status} (ROADMAP E.1). */
public enum UserStatus {
    /** Invited, no password set yet — {@code POST /api/auth/activate} moves it to {@link #ACTIVE}. */
    INVITED,
    ACTIVE,
    BLOCKED
}
