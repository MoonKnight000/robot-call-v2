package uz.murodjon.robotcallv2.profile.domain.entity;

import uz.murodjon.robotcallv2.user.domain.enums.UserRole;

import java.time.Instant;
import java.util.List;

/**
 * Domain model for User Profile (UI-DESIGN §8.3).
 */
public record Profile(
        long id,
        String name,
        String username,
        String email,
        String phone,
        String position,
        Long avatarFileId,
        UserRole role,
        long companyId,
        Instant lastLoginAt,
        Instant createdAt,
        List<String> callColumns
) {
}
