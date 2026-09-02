package uz.murodjon.robotcallv2.user.application.dto;

import java.time.Instant;

public record InviteUserResponse(
        long userId,
        String email,
        String inviteToken,
        Instant expiresAt
) {
}
