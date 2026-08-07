package uz.murodjon.uysotvoice.user.dto;

/**
 * {@code POST /api/users/invite} response. {@code activationToken} is the raw,
 * one-time token — it is never recoverable again after this response (only its
 * hash is persisted), so the admin must relay it to the invitee now (no SMTP
 * integration yet, ROADMAP E.4).
 */
public record InviteUserResponse(UserRow user, String activationToken) {
}
